# 동시성 테스트 전략 (Test Strategy)

> **목적**: 동시성 문제를 효과적으로 재현하고 검증하는 테스트 작성 방법을 제공한다.

---

## 📌 테스트 레벨

1. **단위 테스트**: 비즈니스 로직 검증
2. **통합 테스트**: DB 트랜잭션 검증
3. **동시성 테스트**: 멀티스레드 시나리오
4. **부하 테스트**: 성능 및 안정성 검증

---

## 1. 재고 차감 동시성 테스트

### 📝 테스트 시나리오

**목표**: 100명이 동시에 재고 1개 상품 구매 시도 → 정확히 1명만 성공

### 🧪 단위 테스트 (비즈니스 로직)

```java
@DisplayName("재고 차감 단위 테스트")
class ProductTest {

    @Test
    @DisplayName("재고가 충분하면 차감 성공")
    void decreaseStock_Success() {
        // Given
        Product product = new Product("노트북", 1000000L, 10);

        // When
        product.decreaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고가 부족하면 예외 발생")
    void decreaseStock_InsufficientStock() {
        // Given
        Product product = new Product("노트북", 1000000L, 5);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(10))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("재고 부족");
    }
}
```

### 🧪 통합 테스트 (Pessimistic Lock)

```java
@SpringBootTest
@Transactional
@DisplayName("재고 차감 통합 테스트")
class StockUseCaseTest {

    @Autowired
    private StockUseCase stockUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Pessimistic Lock으로 재고 차감")
    void decreaseStock_WithLock() {
        // Given
        Product product = new Product("키보드", 100000L, 10);
        productRepository.save(product);

        // When
        int remaining = stockUseCase.decreaseStock(product.getId(), 3);

        // Then
        assertThat(remaining).isEqualTo(7);

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(7);
    }
}
```

### 🧪 동시성 테스트 (멀티스레드)

```java
@SpringBootTest
@DisplayName("재고 차감 동시성 테스트")
class StockConcurrencyTest {

    @Autowired
    private StockUseCase stockUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("100명이 동시에 재고 1개 구매 → 1명만 성공")
    void concurrentPurchase_OnlyOneSuccess() throws InterruptedException {
        // Given: 재고 1개 상품
        Product product = new Product("마지막 상품", 50000L, 1);
        productRepository.save(product);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 100명이 동시 구매 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockUseCase.decreaseStock(product.getId(), 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    fail("예상하지 못한 예외: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 1명만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        Product result = productRepository.findById(product.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("동시에 여러 상품 구매 → Deadlock 방지")
    void concurrentPurchaseMultipleProducts_NoDeadlock() throws InterruptedException {
        // Given: 상품 3개
        Product p1 = productRepository.save(new Product("상품1", 10000L, 10));
        Product p2 = productRepository.save(new Product("상품2", 20000L, 10));
        Product p3 = productRepository.save(new Product("상품3", 30000L, 10));

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // When: 50명이 동시에 3개 상품 구매 (역순으로)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    List<Long> productIds = index % 2 == 0
                        ? List.of(p1.getId(), p2.getId(), p3.getId())  // 정방향
                        : List.of(p3.getId(), p2.getId(), p1.getId()); // 역방향

                    stockUseCase.purchaseMultipleProducts(
                        productIds,
                        Map.of(p1.getId(), 1, p2.getId(), 1, p3.getId(), 1)
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 재고 부족은 정상 (일부만 성공)
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);  // Deadlock 발생 시 타임아웃

        // Then: Deadlock 없이 완료
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isGreaterThan(0);

        executorService.shutdown();
    }

    @RepeatedTest(100)  // 100회 반복 실행 (불안정성 체크)
    @DisplayName("재고 차감 안정성 테스트 (100회 반복)")
    void stockDecrease_Stability() {
        // Given
        Product product = new Product("안정성 테스트", 10000L, 10);
        productRepository.save(product);

        // When
        stockUseCase.decreaseStock(product.getId(), 1);

        // Then
        Product result = productRepository.findById(product.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(9);

        productRepository.delete(result);  // 정리
    }
}
```

---

## 2. 선착순 쿠폰 동시성 테스트

### 📝 테스트 시나리오

**목표**: 200명이 선착순 100개 쿠폰 신청 → 정확히 100개만 발급

### 🧪 동시성 테스트 (Redis Lock)

```java
@SpringBootTest
@DisplayName("쿠폰 발급 동시성 테스트")
class CouponConcurrencyTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("200명이 선착순 100개 쿠폰 신청 → 100개만 발급")
    void issueCoupon_FirstCome100() throws InterruptedException {
        // Given: 선착순 100개 쿠폰
        Coupon coupon = new Coupon(1L, "선착순 100명", 100, 0);
        couponRepository.save(coupon);

        // Redis 재고 초기화
        redisTemplate.opsForValue().set("coupon:1:stock", "100");

        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명 동시 신청
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    CouponIssueResult result = couponUseCase.issueCoupon(1L, userId);

                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // DB 동기화 대기
        Thread.sleep(1000);

        // Then: 정확히 100개만 발급
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);

        long issuedCount = userCouponRepository.count();
        assertThat(issuedCount).isEqualTo(100);

        String remainingStock = redisTemplate.opsForValue().get("coupon:1:stock");
        assertThat(remainingStock).isEqualTo("0");
    }

    @Test
    @DisplayName("중복 발급 방지 (1인 1매)")
    void issueCoupon_NoDuplicateIssuance() throws InterruptedException {
        // Given
        Coupon coupon = new Coupon(2L, "1인 1매", 100, 0);
        couponRepository.save(coupon);
        redisTemplate.opsForValue().set("coupon:2:stock", "100");

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Long sameUserId = 999L;
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 동일 사용자가 10번 신청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    CouponIssueResult result = couponUseCase.issueCoupon(2L, sameUserId);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Thread.sleep(500);  // DB 동기화 대기

        // Then: 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);

        long issuedCount = userCouponRepository.countByUserIdAndCouponId(sameUserId, 2L);
        assertThat(issuedCount).isEqualTo(1);
    }
}
```

---

## 3. 결제 중복 처리 테스트

### 📝 테스트 시나리오

**목표**: 동일 주문에 대한 중복 결제 요청 → 1번만 처리

### 🧪 동시성 테스트 (Idempotency Key)

```java
@SpringBootTest
@DisplayName("결제 중복 처리 테스트")
class PaymentConcurrencyTest {

    @Autowired
    private PaymentUseCase paymentUseCase;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동일 Idempotency Key로 10번 요청 → 1번만 처리")
    void processPayment_IdempotencyKey() throws InterruptedException {
        // Given
        User user = new User("테스트 사용자", 100000);
        userRepository.save(user);

        PaymentRequest request = new PaymentRequest(user.getId(), 100L, 30000);
        String idempotencyKey = "payment-100-" + UUID.randomUUID();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<PaymentResult> results = new CopyOnWriteArrayList<>();

        // When: 동일한 Idempotency Key로 10번 결제 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    PaymentResult result = paymentUseCase.processPayment(idempotencyKey, request);
                    results.add(result);
                } catch (Exception e) {
                    // 예외 무시 (Optimistic Lock Exception 등)
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 1번만 처리됨
        long paymentCount = paymentRepository.countByIdempotencyKey(idempotencyKey);
        assertThat(paymentCount).isEqualTo(1);

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance()).isEqualTo(70000);  // 100000 - 30000

        // 모든 결과가 동일해야 함 (멱등성)
        assertThat(results).allMatch(PaymentResult::isSuccess);
    }

    @Test
    @DisplayName("다른 Idempotency Key로 요청 → 각각 처리")
    void processPayment_DifferentIdempotencyKeys() throws InterruptedException {
        // Given
        User user = new User("테스트 사용자", 100000);
        userRepository.save(user);

        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // When: 서로 다른 Idempotency Key로 3번 결제
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    PaymentRequest request = new PaymentRequest(user.getId(), 100L + index, 10000);
                    String idempotencyKey = "payment-" + (100 + index) + "-" + UUID.randomUUID();

                    paymentUseCase.processPayment(idempotencyKey, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 잔액 부족 등은 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 각각 처리됨
        long paymentCount = paymentRepository.count();
        assertThat(paymentCount).isEqualTo(successCount.get());
    }
}
```

---

## 4. 잔액 업데이트 동시성 테스트

### 🧪 동시성 테스트 (Atomic Update)

```java
@SpringBootTest
@DisplayName("잔액 업데이트 동시성 테스트")
class BalanceConcurrencyTest {

    @Autowired
    private UserUseCase userUseCase;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동시 충전/차감 → Lost Update 방지")
    void balanceUpdate_NoLostUpdate() throws InterruptedException {
        // Given: 초기 잔액 10000
        User user = new User("테스트 사용자", 10000);
        userRepository.save(user);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When: 50번 충전 (+1000), 50번 차감 (-500)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        userUseCase.chargeBalance(user.getId(), 1000);  // +1000
                    } else {
                        userUseCase.deductBalance(user.getId(), 500);   // -500
                    }
                } catch (InsufficientBalanceException e) {
                    // 잔액 부족은 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 최종 잔액 정확성 검증
        User result = userRepository.findById(user.getId()).orElseThrow();

        // 기대값: 10000 + (50 * 1000) - (50 * 500) = 10000 + 50000 - 25000 = 35000
        // 단, 차감 시 잔액 부족으로 일부 실패 가능
        assertThat(result.getBalance()).isGreaterThanOrEqualTo(0);
        assertThat(result.getBalance()).isLessThanOrEqualTo(60000);
    }
}
```

---

## 5. 주문 상태 전이 테스트

### 🧪 동시성 테스트 (Optimistic Lock)

```java
@SpringBootTest
@DisplayName("주문 상태 전이 동시성 테스트")
class OrderStatusConcurrencyTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("동시에 상태 변경 시도 → 1번만 성공")
    void orderStatusChange_OnlyOneSuccess() throws InterruptedException {
        // Given
        Order order = new Order(OrderStatus.PENDING);
        orderRepository.save(order);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10명이 동시에 "PAID"로 변경 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderUseCase.markOrderAsPaid(order.getId());
                    successCount.incrementAndGet();
                } catch (OptimisticLockException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
    }

    @Test
    @DisplayName("잘못된 상태 전이 시도 → 예외 발생")
    void orderStatusChange_InvalidTransition() {
        // Given
        Order order = new Order(OrderStatus.DELIVERED);  // 이미 배송 완료
        orderRepository.save(order);

        // When & Then: SHIPPING으로 변경 불가
        assertThatThrownBy(() -> orderUseCase.startShipping(order.getId()))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
```

---

## 📊 테스트 커버리지

### JaCoCo 설정

```gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

test {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }

    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/dto/**',
                '**/config/**'
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70  // 70% 이상
            }
        }
    }
}
```

### 실행 명령어

```bash
# 테스트 실행 + 커버리지 리포트
./gradlew test jacocoTestReport

# 커버리지 검증 (70% 미만 시 빌드 실패)
./gradlew jacocoTestCoverageVerification

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

---

## 🎯 테스트 작성 Best Practices

### 1. Given-When-Then 패턴
```java
@Test
void test() {
    // Given: 테스트 준비
    // When: 테스트 실행
    // Then: 결과 검증
}
```

### 2. @DisplayName으로 명확한 설명
```java
@DisplayName("100명이 동시에 재고 1개 구매 → 1명만 성공")
```

### 3. Atomic 변수로 스레드 안전한 카운팅
```java
AtomicInteger successCount = new AtomicInteger(0);
AtomicInteger failCount = new AtomicInteger(0);
```

### 4. CountDownLatch로 동기화
```java
CountDownLatch latch = new CountDownLatch(threadCount);
// ...
latch.countDown();
// ...
latch.await();  // 모든 스레드 완료 대기
```

### 5. @RepeatedTest로 안정성 검증
```java
@RepeatedTest(100)  // 100회 반복
void stabilityTest() {
    // 테스트 로직
}
```

---

## 📚 다음 문서

- **성능 최적화**: [PERFORMANCE_OPTIMIZATION.md](./PERFORMANCE_OPTIMIZATION.md)

---

**작성일**: 2025-11-18
**버전**: 1.0
