# 도메인 단위 Integration Test 전략

**작성일**: 2025-12-14
**상태**: 🔄 **전략 수립 중**
**목표**: 기존 98개 실패 테스트 → 도메인별 핵심 시나리오 테스트로 재설계

---

## 📋 전략 개요

### 기존 문제점
- ❌ 98개 Integration Test 실패
- ❌ TransactionTemplate 복잡도 높음
- ❌ Transaction Manager 미스매치 (DataSource vs JPA)
- ❌ 유지보수 어려움

### 새로운 접근
- ✅ 도메인별 핵심 시나리오만 테스트
- ✅ TransactionTemplate 제거
- ✅ Infrastructure 의존성 최소화
- ✅ 명확한 테스트 범위

---

## 🎯 도메인별 핵심 시나리오

### 1. Product 도메인 (상품)

#### 핵심 시나리오
1. **재고 차감 동시성 제어**
   - 동시 주문 시 재고 정확성 보장
   - Pessimistic Lock 검증

2. **상품 랭킹 갱신**
   - 결제 완료 후 Redis 랭킹 업데이트
   - 비동기 처리 검증

#### 제안 테스트
```java
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class ProductIntegrationTest {

    @Test
    @DisplayName("동시 주문 시 재고 차감 정확성")
    void concurrentOrderStockDeduction() {
        // Given: 재고 100개 상품
        Product product = createProduct("P001", 100);

        // When: 10개 스레드가 동시에 10개씩 주문
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    orderService.createOrder(userId, product.getId(), 10);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // Then: 재고는 정확히 0
        Product updated = productRepository.findById(product.getId()).get();
        assertThat(updated.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("결제 완료 후 랭킹 갱신 (비동기)")
    void paymentCompletedRankingUpdate() throws InterruptedException {
        // Given: 상품 및 주문
        Product product = createProduct("P002", 100);
        Order order = createOrder(userId, product.getId(), 5);

        // When: 결제 완료
        paymentService.processPayment(order.getId());

        // Then: 2초 대기 후 랭킹 확인
        Thread.sleep(2000);

        int score = rankingRepository.getScore(LocalDate.now(), product.getId().toString());
        assertThat(score).isEqualTo(5);
    }
}
```

---

### 2. Order 도메인 (주문)

#### 핵심 시나리오
1. **주문 생성 흐름**
   - 재고 확인 → 주문 생성 → 재고 차감
   - 재고 부족 시 실패

2. **결제 처리 흐름**
   - 잔액 확인 → 결제 → 잔액 차감
   - 잔액 부족 시 실패

3. **결제 완료 이벤트 발행**
   - 멱등성 체크
   - 랭킹 갱신 트리거

#### 제안 테스트
```java
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Test
    @DisplayName("주문 생성 → 결제 → 이벤트 발행")
    void orderCreationToPaymentFlow() {
        // Given: 사용자 잔액 충전
        User user = createUser("user@example.com");
        user.charge(100_000L);
        userRepository.save(user);

        // Given: 상품 생성
        Product product = createProduct("P001", 10_000L, 100);

        // When: 주문 생성
        Order order = orderService.createOrder(user.getId(), product.getId(), 3);

        // When: 결제 처리
        paymentService.processPayment(order.getId());

        // Then: 주문 상태 COMPLETED
        Order completed = orderRepository.findById(order.getId()).get();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Then: 사용자 잔액 차감
        User updatedUser = userRepository.findById(user.getId()).get();
        assertThat(updatedUser.getBalance()).isEqualTo(70_000L);

        // Then: 재고 차감
        Product updatedProduct = productRepository.findById(product.getId()).get();
        assertThat(updatedProduct.getStock()).isEqualTo(97);
    }

    @Test
    @DisplayName("재고 부족 시 주문 실패")
    void orderFailsWhenInsufficientStock() {
        // Given: 재고 5개 상품
        Product product = createProduct("P002", 10_000L, 5);

        // When & Then: 10개 주문 시도 → 예외
        assertThatThrownBy(() ->
            orderService.createOrder(userId, product.getId(), 10)
        ).isInstanceOf(InsufficientStockException.class);
    }
}
```

---

### 3. User 도메인 (사용자)

#### 핵심 시나리오
1. **잔액 충전 동시성 제어**
   - 동시 충전 요청 시 정확성 보장
   - Optimistic Lock 검증

2. **잔액 차감 동시성 제어**
   - 결제 시 잔액 부족 방지

#### 제안 테스트
```java
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class UserIntegrationTest {

    @Test
    @DisplayName("동시 충전 요청 시 잔액 정확성")
    void concurrentChargeAccuracy() throws InterruptedException {
        // Given: 초기 잔액 0원
        User user = createUser("user@example.com");

        // When: 10개 스레드가 동시에 10,000원씩 충전
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    userService.charge(user.getId(), 10_000L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // Then: 잔액 정확히 100,000원
        User updated = userRepository.findById(user.getId()).get();
        assertThat(updated.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("잔액 부족 시 결제 실패")
    void paymentFailsWhenInsufficientBalance() {
        // Given: 잔액 5,000원
        User user = createUser("user@example.com");
        user.charge(5_000L);
        userRepository.save(user);

        // Given: 10,000원 상품
        Product product = createProduct("P001", 10_000L, 100);
        Order order = createOrder(user.getId(), product.getId(), 1);

        // When & Then: 결제 시도 → 예외
        assertThatThrownBy(() ->
            paymentService.processPayment(order.getId())
        ).isInstanceOf(InsufficientBalanceException.class);
    }
}
```

---

### 4. Event 도메인 (이벤트)

#### 핵심 시나리오
1. **멱등성 체크**
   - 중복 이벤트 필터링
   - ProcessedEvent DB 확인

2. **랭킹 갱신 재시도**
   - Redis 일시 장애 시 재시도
   - 최종 실패 시 DLQ 저장

3. **DLQ 저장**
   - 복구 불가 에러 저장

#### 제안 테스트
```java
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class EventIntegrationTest {

    @Test
    @DisplayName("중복 이벤트 필터링")
    void duplicateEventFiltering() {
        // Given: 이벤트 발행
        PaymentCompletedEvent event = createEvent(orderId);
        eventPublisher.publishEvent(event);

        // When: 동일 이벤트 재발행
        // Then: DuplicateEventException 발생
        assertThatThrownBy(() ->
            eventPublisher.publishEvent(event)
        ).isInstanceOf(DuplicateEventException.class);
    }

    @Test
    @DisplayName("랭킹 갱신 성공 후 멱등성 기록")
    void rankingUpdateWithIdempotency() throws InterruptedException {
        // Given: 주문 및 결제
        Order order = createAndPayOrder(userId, productId, 3);

        // When: 이벤트 발행
        eventPublisher.publishEvent(new PaymentCompletedEvent(order));

        // Then: 2초 대기 후 랭킹 확인
        Thread.sleep(2000);
        int score = rankingRepository.getScore(LocalDate.now(), productId.toString());
        assertThat(score).isEqualTo(3);

        // Then: 멱등성 기록 확인
        String eventId = "order-" + order.getId();
        boolean exists = processedEventRepository.exists(eventId);
        assertThat(exists).isTrue();
    }
}
```

---

## 🔧 테스트 구조 개선 사항

### 1. TransactionTemplate 제거
**Before**:
```java
TransactionTemplate template = new TransactionTemplate(transactionManager);
template.execute(status -> {
    // 복잡한 트랜잭션 로직
    User user = userRepository.save(user);
    testUserId = user.getId();  // detached 문제
    return null;
});
```

**After**:
```java
// Service 레이어에서 트랜잭션 처리
User user = userService.createUser("user@example.com");
// Service는 이미 @Transactional이므로 별도 처리 불필요
```

---

### 2. 헬퍼 메서드 활용
```java
abstract class IntegrationTestBase {

    protected User createUser(String email) {
        User user = User.create(email, "테스트유저");
        return userRepository.save(user);
    }

    protected Product createProduct(String code, long price, int stock) {
        Product product = Product.create(code, "상품", "설명", price, "전자제품", stock);
        return productRepository.save(product);
    }

    protected Order createOrder(Long userId, Long productId, int quantity) {
        return orderService.createOrder(userId, productId, quantity);
    }

    protected void chargeUser(Long userId, long amount) {
        userService.charge(userId, amount);
    }
}
```

---

### 3. 비동기 검증 전략
```java
// Before: Thread.sleep(2000) - 불확실
Thread.sleep(2000);
int score = rankingRepository.getScore(...);

// After: Awaitility 라이브러리 사용
await().atMost(3, TimeUnit.SECONDS)
    .pollInterval(100, TimeUnit.MILLISECONDS)
    .untilAsserted(() -> {
        int score = rankingRepository.getScore(...);
        assertThat(score).isEqualTo(5);
    });
```

**의존성 추가 (선택)**:
```gradle
testImplementation 'org.awaitility:awaitility:4.2.0'
```

---

## 📊 테스트 범위 축소

### 기존 (98개 테스트)
- 각 컨트롤러별 Integration Test (15개)
- 각 Use Case별 Integration Test (20개)
- 각 리스너별 Integration Test (10개)
- 동시성 테스트 (8개)
- 기타 (45개)

### 새로운 (예상 20-30개)
- **Product 도메인** (5-7개)
  - 재고 동시성
  - 랭킹 갱신
  - 상품 조회

- **Order 도메인** (8-10개)
  - 주문 생성 흐름
  - 결제 흐름
  - 재고/잔액 부족 처리

- **User 도메인** (3-5개)
  - 잔액 충전 동시성
  - 잔액 차감

- **Event 도메인** (4-6개)
  - 멱등성
  - 재시도
  - DLQ

---

## 🚀 구현 순서

### Phase 1: Product 도메인 (우선순위 높음)
- [ ] `ProductIntegrationTest.java` 작성
- [ ] 재고 동시성 테스트
- [ ] 랭킹 갱신 비동기 테스트

### Phase 2: Order 도메인
- [ ] `OrderIntegrationTest.java` 작성
- [ ] 주문 생성 → 결제 흐름
- [ ] 재고/잔액 부족 케이스

### Phase 3: User 도메인
- [ ] `UserIntegrationTest.java` 작성
- [ ] 잔액 충전 동시성
- [ ] 잔액 차감 검증

### Phase 4: Event 도메인
- [ ] `EventIntegrationTest.java` 작성
- [ ] 멱등성 체크
- [ ] 재시도 메커니즘
- [ ] DLQ 저장

---

## 🎯 성공 기준

### 테스트 품질
- [ ] 각 도메인별 핵심 시나리오 커버
- [ ] 동시성 제어 검증
- [ ] 비동기 처리 검증
- [ ] 실패 케이스 처리

### 유지보수성
- [ ] TransactionTemplate 제거
- [ ] 헬퍼 메서드 활용
- [ ] 명확한 Given-When-Then 구조
- [ ] 적절한 테스트 격리

### 실행 속도
- [ ] 전체 테스트 3분 이내
- [ ] Testcontainers 최적화
- [ ] 병렬 실행 가능

---

## 💡 참고 사항

### Testcontainers 최적화
```java
@Testcontainers
@SpringBootTest
abstract class IntegrationTestBase {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withReuse(true);  // 컨테이너 재사용

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);
}
```

### 기존 98개 테스트 처리
- **보존**: 문서화 목적으로 `src/test/java/archive/` 이동
- **삭제 금지**: 향후 참고 자료
- **새로운 테스트**: `src/test/java/.../integration/` 패키지

---

**작성자**: Claude Code
**최종 수정**: 2025-12-14
**상태**: 🔄 **전략 수립 완료**, 🚀 **구현 대기 중**
**다음 단계**: Product 도메인 Integration Test 작성
