# Redis 기능 테스트 가이드

## 개요

Testcontainers를 활용하여 Redis 기반 랭킹/쿠폰 기능의 동시성과 정합성을 검증합니다.

---

## Testcontainers 설정

### 의존성 추가 (build.gradle)
```gradle
testImplementation 'org.testcontainers:testcontainers:1.19.3'
testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
testImplementation 'org.testcontainers:mysql:1.19.3'
testImplementation 'com.redis:testcontainers-redis:2.0.1'
```

### 베이스 테스트 클래스
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class RedisIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    protected RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void clearRedis() {
        // 각 테스트 전 Redis 초기화
        redisTemplate.getConnectionFactory()
            .getConnection()
            .serverCommands()
            .flushAll();
    }
}
```

---

## 랭킹 시스템 테스트

### 1. 기본 랭킹 갱신 테스트
```java
@Test
void 상품_랭킹_갱신_테스트() {
    // Given
    String key = "ranking:product:orders:daily:20250102";
    String productId = "123";

    // When
    redisTemplate.opsForZSet().incrementScore(key, productId, 10);
    redisTemplate.opsForZSet().incrementScore(key, productId, 5);

    // Then
    Double score = redisTemplate.opsForZSet().score(key, productId);
    assertThat(score).isEqualTo(15.0);
}
```

### 2. 동시성 테스트 (다중 스레드)
```java
@Test
void 랭킹_동시_갱신_정합성_테스트() throws InterruptedException {
    // Given
    String key = "ranking:product:orders:daily:20250102";
    String productId = "123";
    int threadCount = 100;
    int incrementPerThread = 10;

    // When: 100개 스레드가 각각 10씩 증가
    ExecutorService executorService = Executors.newFixedThreadPool(50);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                redisTemplate.opsForZSet()
                    .incrementScore(key, productId, incrementPerThread);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executorService.shutdown();

    // Then: 정확히 1000이어야 함 (100 * 10)
    Double score = redisTemplate.opsForZSet().score(key, productId);
    assertThat(score).isEqualTo(1000.0);
}
```

### 3. Top N 조회 테스트
```java
@Test
void Top10_랭킹_조회_테스트() {
    // Given
    String key = "ranking:product:orders:daily:20250102";

    redisTemplate.opsForZSet().add(key, "product1", 100);
    redisTemplate.opsForZSet().add(key, "product2", 200);
    redisTemplate.opsForZSet().add(key, "product3", 150);
    redisTemplate.opsForZSet().add(key, "product4", 300);
    redisTemplate.opsForZSet().add(key, "product5", 50);

    // When: Top 3 조회
    Set<ZSetOperations.TypedTuple<String>> top3 =
        redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 2);

    // Then
    assertThat(top3).hasSize(3);

    List<String> productIds = top3.stream()
        .map(ZSetOperations.TypedTuple::getValue)
        .toList();

    assertThat(productIds).containsExactly("product4", "product2", "product3");
}
```

### 4. TTL 검증 테스트
```java
@Test
void 랭킹_키_TTL_설정_테스트() {
    // Given
    String key = "ranking:product:orders:daily:20250102";
    redisTemplate.opsForZSet().add(key, "product1", 100);

    // When: 3일 후 만료 설정
    redisTemplate.expire(key, 3, TimeUnit.DAYS);

    // Then
    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
    assertThat(ttl).isBetween(259199L, 259201L);  // 약 3일 (±1초)
}
```

---

## 쿠폰 발급 시스템 테스트

### 1. 기본 쿠폰 발급 테스트
```java
@Test
void 쿠폰_발급_성공_테스트() {
    // Given
    Long couponId = 1L;
    Long userId = 123L;
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    redisTemplate.opsForValue().set(remainKey, "10");

    // When
    CouponIssueResult result = couponIssueService.issueCoupon(couponId, userId);

    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(redisTemplate.opsForValue().get(remainKey)).isEqualTo("9");
    assertThat(redisTemplate.opsForSet().isMember(issuedKey, userId.toString()))
        .isTrue();
}
```

### 2. 수량 부족 테스트
```java
@Test
void 쿠폰_수량_부족_테스트() {
    // Given
    Long couponId = 1L;
    Long userId = 123L;
    String remainKey = "coupon:" + couponId + ":remain";

    redisTemplate.opsForValue().set(remainKey, "0");

    // When
    CouponIssueResult result = couponIssueService.issueCoupon(couponId, userId);

    // Then
    assertThat(result.getErrorCode()).isEqualTo("COUPON_SOLD_OUT");
    assertThat(redisTemplate.opsForValue().get(remainKey)).isEqualTo("0");  // 변동 없음
}
```

### 3. 중복 발급 방지 테스트
```java
@Test
void 쿠폰_중복_발급_방지_테스트() {
    // Given
    Long couponId = 1L;
    Long userId = 123L;
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    redisTemplate.opsForValue().set(remainKey, "10");

    // When: 같은 사용자가 2번 발급 시도
    CouponIssueResult result1 = couponIssueService.issueCoupon(couponId, userId);
    CouponIssueResult result2 = couponIssueService.issueCoupon(couponId, userId);

    // Then
    assertThat(result1.isSuccess()).isTrue();
    assertThat(result2.getErrorCode()).isEqualTo("ALREADY_ISSUED");

    // 수량은 1번만 차감
    assertThat(redisTemplate.opsForValue().get(remainKey)).isEqualTo("9");

    // 발급 기록은 1개만
    Long issuedCount = redisTemplate.opsForSet().size(issuedKey);
    assertThat(issuedCount).isEqualTo(1);
}
```

### 4. 동시성 테스트 (핵심)
```java
@Test
void 선착순_쿠폰_발급_동시성_테스트() throws InterruptedException {
    // Given
    Long couponId = 1L;
    int totalQuantity = 100;
    int threadCount = 1000;
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    redisTemplate.opsForValue().set(remainKey, String.valueOf(totalQuantity));

    // When: 1000개 스레드가 동시에 발급 시도
    ExecutorService executorService = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        long userId = i;
        executorService.submit(() -> {
            try {
                CouponIssueResult result = couponIssueService.issueCoupon(couponId, userId);

                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(10, TimeUnit.SECONDS);
    executorService.shutdown();

    // Then
    assertThat(successCount.get()).isEqualTo(totalQuantity);  // 정확히 100개만 성공
    assertThat(failCount.get()).isEqualTo(900);  // 나머지는 실패

    String remain = redisTemplate.opsForValue().get(remainKey);
    assertThat(remain).isEqualTo("0");  // 남은 수량 0

    Long issuedCount = redisTemplate.opsForSet().size(issuedKey);
    assertThat(issuedCount).isEqualTo(totalQuantity);  // 발급 기록 정확히 100개

    // 추가 검증: 수량이 마이너스가 되지 않았는지
    assertThat(Integer.parseInt(remain)).isGreaterThanOrEqualTo(0);
}
```

### 5. 롤백 테스트 (개별 명령 방식)
```java
@Test
void 쿠폰_발급_중_오류_발생_시_롤백_테스트() {
    // Given
    Long couponId = 1L;
    Long userId = 123L;
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    redisTemplate.opsForValue().set(remainKey, "10");

    // Redis Mock을 통해 SADD 실패 시뮬레이션
    doThrow(new RedisConnectionFailureException("Network error"))
        .when(redisTemplate.opsForSet())
        .add(eq(issuedKey), any());

    // When
    assertThrows(CouponIssueException.class, () -> {
        couponIssueService.issueCoupon(couponId, userId);
    });

    // Then: 수량이 원복되어야 함
    String remain = redisTemplate.opsForValue().get(remainKey);
    assertThat(remain).isEqualTo("10");  // 원래 수량 유지
}
```

---

## 통합 테스트 (전체 플로우)

### 주문 완료 → 랭킹 갱신 플로우
```java
@Test
void 주문_완료_후_랭킹_자동_갱신_테스트() {
    // Given
    Long userId = 1L;
    Long productId = 123L;
    int quantity = 5;

    CreateOrderRequest request = CreateOrderRequest.builder()
        .userId(userId)
        .items(List.of(
            new OrderItemRequest(productId, quantity, 10000)
        ))
        .build();

    // When: 주문 생성 및 결제
    OrderResponse order = orderUseCase.createOrder(request);
    paymentUseCase.processPayment(order.getOrderId(), userId);

    // Then: 랭킹이 자동으로 갱신되었는지 확인
    String key = "ranking:product:orders:daily:" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    await().atMost(3, TimeUnit.SECONDS)
        .untilAsserted(() -> {
            Double score = redisTemplate.opsForZSet().score(key, productId.toString());
            assertThat(score).isEqualTo(5.0);
        });
}
```

### 쿠폰 발급 → 주문 적용 플로우
```java
@Test
void 쿠폰_발급_후_주문_적용_테스트() {
    // Given
    Long userId = 1L;
    Long couponId = 1L;
    String remainKey = "coupon:" + couponId + ":remain";

    redisTemplate.opsForValue().set(remainKey, "10");

    // When: 쿠폰 발급
    CouponIssueResult issueResult = couponIssueService.issueCoupon(couponId, userId);
    assertThat(issueResult.isSuccess()).isTrue();

    // 쿠폰 적용하여 주문
    CreateOrderRequest request = CreateOrderRequest.builder()
        .userId(userId)
        .items(List.of(new OrderItemRequest(123L, 1, 10000)))
        .couponId(couponId)
        .build();

    OrderResponse order = orderUseCase.createOrder(request);

    // Then
    assertThat(order.getDiscountAmount()).isGreaterThan(0);
    assertThat(order.getTotalAmount()).isLessThan(10000);
}
```

---

## 성능 테스트

### 랭킹 갱신 성능 측정
```java
@Test
void 랭킹_갱신_성능_측정() {
    String key = "ranking:product:orders:daily:20250102";
    int iterations = 10000;

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < iterations; i++) {
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(i % 100), 1);
    }

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    System.out.println("10,000회 ZINCRBY: " + duration + "ms");
    assertThat(duration).isLessThan(5000);  // 5초 이내
}
```

### 쿠폰 발급 처리량 측정
```java
@Test
void 쿠폰_발급_처리량_측정() throws InterruptedException {
    Long couponId = 1L;
    int totalQuantity = 1000;
    String remainKey = "coupon:" + couponId + ":remain";

    redisTemplate.opsForValue().set(remainKey, String.valueOf(totalQuantity));

    ExecutorService executorService = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(totalQuantity);
    AtomicInteger successCount = new AtomicInteger(0);

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < totalQuantity; i++) {
        long userId = i;
        executorService.submit(() -> {
            try {
                CouponIssueResult result = couponIssueService.issueCoupon(couponId, userId);
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    executorService.shutdown();

    System.out.println("1,000건 쿠폰 발급: " + duration + "ms");
    System.out.println("TPS: " + (1000.0 / duration * 1000) + " req/s");

    assertThat(successCount.get()).isEqualTo(totalQuantity);
    assertThat(duration).isLessThan(10000);  // 10초 이내
}
```

---

## 주의사항

### 1. 테스트 격리
- `@BeforeEach`에서 Redis 데이터 초기화 필수
- 테스트 간 데이터 간섭 방지

### 2. 비동기 처리 검증
- `Awaitility` 라이브러리 활용
```gradle
testImplementation 'org.awaitility:awaitility:4.2.0'
```

### 3. Testcontainers 재사용
- `.withReuse(true)` 옵션으로 컨테이너 재사용
- 테스트 속도 향상

### 4. 타임아웃 설정
- `latch.await(10, TimeUnit.SECONDS)` 타임아웃 필수
- 무한 대기 방지

---

## 참고: 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# Redis 테스트만 실행
./gradlew test --tests '*Redis*'

# 특정 테스트 클래스 실행
./gradlew test --tests CouponIssueServiceTest

# 커버리지 리포트
./gradlew test jacocoTestReport
```

---

## 참고 자료

- [Testcontainers 공식 문서](https://java.testcontainers.org/)
- [Awaitility 가이드](https://github.com/awaitility/awaitility)
- [Redis Testcontainers](https://java.testcontainers.org/modules/databases/redis/)
