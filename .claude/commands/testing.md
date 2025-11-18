---
description: 테스트 전략 및 Jacoco 커버리지 가이드
---

# Testing Strategy

> Week 3 테스트 커버리지 70% 이상 달성 가이드

## 🧪 테스트 전략

### 1. Domain Layer 테스트 (Mock 불필요)

Entity 메서드는 **순수 Java 클래스**이므로 의존성 없이 테스트 가능합니다.

```java
class ProductTest {

    @Test
    void 재고_차감_성공() {
        // Given
        Product product = new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");

        // When
        product.decreaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void 재고_부족시_예외_발생() {
        // Given
        Product product = new Product("P001", "노트북", "설명", 890000L, 5, "전자제품");

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void 재고_복구_성공() {
        // Given
        Product product = new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");
        product.decreaseStock(3);

        // When
        product.restoreStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void 재고_확인() {
        // Given
        Product product = new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");

        // When & Then
        assertThat(product.hasStock(5)).isTrue();
        assertThat(product.hasStock(15)).isFalse();
    }
}
```

---

### 2. Application Layer 테스트 (Mock 사용)

UseCase는 Repository에 의존하므로 **Mockito**로 격리 테스트합니다.

```java
@ExtendWith(MockitoExtension.class)
class ProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductUseCase productUseCase;

    @Test
    void 상품_조회_성공() {
        // Given
        String productId = "P001";
        Product product = new Product(productId, "노트북", "설명", 890000L, 10, "전자제품");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When
        ProductResponse response = productUseCase.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getName()).isEqualTo("노트북");
        assertThat(response.getPrice()).isEqualTo(890000L);
        verify(productRepository).findById(productId);
    }

    @Test
    void 상품_없음_예외_발생() {
        // Given
        String productId = "INVALID";
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productUseCase.getProduct(productId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
        verify(productRepository).findById(productId);
    }

    @Test
    void 상품_목록_조회_카테고리_필터링() {
        // Given
        String category = "전자제품";
        List<Product> products = List.of(
            new Product("P001", "노트북", "설명", 890000L, 10, category),
            new Product("P004", "모니터", "설명", 350000L, 15, category)
        );
        when(productRepository.findByCategory(category)).thenReturn(products);

        // When
        List<ProductResponse> responses = productUseCase.getProducts(category, null);

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses).extracting("category").containsOnly(category);
        verify(productRepository).findByCategory(category);
    }
}
```

---

### 3. Infrastructure Layer 테스트 (선택)

In-Memory Repository는 단순 CRUD이므로 **생략 가능**하지만, 작성하면 커버리지 향상에 도움됩니다.

```java
class InMemoryProductRepositoryTest {

    private InMemoryProductRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductRepository();
    }

    @Test
    void 상품_저장_및_조회() {
        // Given
        Product product = new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");

        // When
        repository.save(product);

        // Then
        Optional<Product> found = repository.findById("P001");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("노트북");
    }

    @Test
    void 전체_상품_조회() {
        // Given
        repository.save(new Product("P001", "노트북", "설명", 890000L, 10, "전자제품"));
        repository.save(new Product("P002", "키보드", "설명", 120000L, 20, "주변기기"));

        // When
        List<Product> products = repository.findAll();

        // Then
        assertThat(products).hasSize(2);
    }

    @Test
    void 카테고리별_조회() {
        // Given
        repository.save(new Product("P001", "노트북", "설명", 890000L, 10, "전자제품"));
        repository.save(new Product("P002", "키보드", "설명", 120000L, 20, "주변기기"));

        // When
        List<Product> electronics = repository.findByCategory("전자제품");

        // Then
        assertThat(electronics).hasSize(1);
        assertThat(electronics.get(0).getId()).isEqualTo("P001");
    }
}
```

---

### 4. 통합 테스트 (Integration Test)

#### 일반 통합 테스트

```java
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeAll
    void setUp() {
        // 테스트 데이터 초기화
        productRepository.save(new Product("P001", "노트북", "설명", 890000L, 10, "전자제품"));
        userRepository.save(new User("U001", "김항해", 2000000L));
    }

    @Test
    void 주문_생성_성공() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            "U001",
            List.of(new OrderItemRequest("P001", 2)),
            null
        );

        // When
        OrderResponse response = orderUseCase.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalAmount()).isEqualTo(1780000L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 재고_부족시_주문_실패() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
            "U001",
            List.of(new OrderItemRequest("P001", 100)),  // 재고 초과
            null
        );

        // When & Then
        assertThatThrownBy(() -> orderUseCase.createOrder(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }
}
```

#### 동시성 테스트

```java
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponUseCase couponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        // 쿠폰 100개 생성
        Coupon coupon = new Coupon("C001", "10% 할인", 10, 100);
        couponRepository.save(coupon);
    }

    @Test
    void 선착순_쿠폰_동시성_테스트() throws InterruptedException {
        // Given: 200명이 동시에 요청
        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            String userId = "U" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    couponUseCase.issueCoupon(userId, "C001");
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 100개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);

        Coupon result = couponRepository.findById("C001").orElseThrow();
        assertThat(result.getIssuedQuantity().get()).isEqualTo(100);
    }
}
```

---

## 📊 Test Coverage Guide (Jacoco)

### build.gradle 설정

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

### 커버리지 확인 명령어

```bash
# 테스트 실행 및 커버리지 리포트 생성
./gradlew test jacocoTestReport

# 커버리지 검증 (70% 미만 시 빌드 실패)
./gradlew jacocoTestCoverageVerification

# 리포트 확인 (Windows)
start build/reports/jacoco/test/html/index.html

# 리포트 확인 (Mac/Linux)
open build/reports/jacoco/test/html/index.html
```

---

## 🎯 70% 커버리지 달성 전략

### 우선순위

1. **Domain Layer (필수)**: 90%+ 목표
   - Entity 메서드 전부 테스트
   - 비즈니스 로직이 핵심

2. **Application Layer (필수)**: 80%+ 목표
   - UseCase 메서드 전부 테스트
   - Mock을 활용한 단위 테스트

3. **Infrastructure Layer (선택)**: 50%+
   - 단순 CRUD는 생략 가능
   - 복잡한 쿼리만 테스트

4. **Presentation Layer (선택)**: 통합 테스트로 대체
   - Controller는 통합 테스트에서 검증
   - 단위 테스트는 생략 가능

### 커버리지 계산 기준

- **라인 커버리지**: 전체 코드 라인 대비 실행된 라인 비율
- **브랜치 커버리지**: if/else 분기 실행 비율

### 예시

```java
public void decreaseStock(int quantity) {
    if (quantity <= 0) {           // 분기 1
        throw new BusinessException("수량은 0보다 커야 함");
    }
    if (stock < quantity) {        // 분기 2
        throw new BusinessException("재고 부족");
    }
    this.stock -= quantity;        // 라인
}
```

**100% 커버리지 달성을 위한 테스트:**
- 테스트 1: quantity = -1 (분기 1: true)
- 테스트 2: quantity = 100, stock = 10 (분기 2: true)
- 테스트 3: quantity = 3, stock = 10 (분기 1: false, 분기 2: false, 라인 실행)

---

## 🚫 Common Pitfalls

### 1. 의미 없는 테스트 작성 (안티 패턴)

```java
// ❌ 나쁨: Getter만 테스트
@Test
void 상품_ID_조회() {
    Product product = new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");
    assertThat(product.getId()).isEqualTo("P001");
}
```

**이유**: Getter는 Lombok이 생성하므로 테스트 불필요

### 2. 통합 테스트만 작성

```java
// ❌ 나쁨: UseCase를 통합 테스트로만 검증
@SpringBootTest
class ProductUseCaseTest {
    @Autowired
    private ProductUseCase productUseCase;

    @Test
    void 상품_조회() {
        // ...
    }
}
```

**문제**: 통합 테스트는 느리고, 격리되지 않음
**해결**: 단위 테스트 + Mock 사용

### 3. 테스트 커버리지에만 집착

```java
// ❌ 나쁨: 커버리지만 높이려는 테스트
@Test
void 의미없는_테스트() {
    new Product("P001", "노트북", "설명", 890000L, 10, "전자제품");
    // 아무 검증도 없음
}
```

**문제**: 커버리지는 높지만 실제로는 아무것도 검증하지 않음
**해결**: 의미 있는 검증(assert) 포함

---

## 🔧 심화: 테스트 품질 vs 수량 (Coach Feedback)

### 테스트 커버리지 94% 달성! 그런데...

**코치님 조언**:
> 테스트 커버리지 94%는 훌륭합니다. 하지만 **수량**뿐만 아니라 **품질**도 중요합니다.
> 의미 있는 Assertion, Edge Case 커버리지, 비즈니스 규칙 철저한 검증에 집중하세요.

---

### 테스트 품질 체크리스트

#### 1. 의미 있는 Assertion (Meaningful Assertions)

❌ **나쁜 예시**: 단순히 null 체크만
```java
@Test
void 상품_조회() {
    Product product = productRepository.findById("P001").orElseThrow();
    assertThat(product).isNotNull();  // 너무 약한 검증
}
```

✅ **좋은 예시**: 구체적인 값 검증
```java
@Test
void 상품_조회_상세정보_확인() {
    Product product = productRepository.findById("P001").orElseThrow();

    // 모든 필드 검증
    assertThat(product.getId()).isEqualTo("P001");
    assertThat(product.getName()).isEqualTo("노트북");
    assertThat(product.getPrice()).isEqualTo(890000L);
    assertThat(product.getStock()).isEqualTo(10);
    assertThat(product.getCategory()).isEqualTo("전자제품");
}
```

---

#### 2. Edge Case 커버리지

✅ **경계값 테스트**:
```java
@Test
void 재고_정확히_0일_때_차감_실패() {
    Product product = new Product("P001", "노트북", "설명", 890000L, 0, "전자제품");

    assertThatThrownBy(() -> product.decreaseStock(1))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
}

@Test
void 재고_정확히_1개_남았을_때_1개_차감_성공() {
    Product product = new Product("P001", "노트북", "설명", 890000L, 1, "전자제품");

    product.decreaseStock(1);

    assertThat(product.getStock()).isEqualTo(0);
}

@Test
void 재고_최대값_테스트() {
    Product product = new Product("P001", "노트북", "설명", 890000L, Integer.MAX_VALUE, "전자제품");

    product.decreaseStock(1);

    assertThat(product.getStock()).isEqualTo(Integer.MAX_VALUE - 1);
}
```

✅ **Null/Empty 처리**:
```java
@Test
void 빈_장바구니_조회() {
    CartResponse response = cartService.getCart("U001");

    assertThat(response).isNotNull();
    assertThat(response.getItems()).isEmpty();
    assertThat(response.getTotalAmount()).isZero();
}

@Test
void 쿠폰_없이_주문_생성() {
    CreateOrderRequest request = new CreateOrderRequest("U001", items, null);  // couponId = null

    OrderResponse response = orderService.createOrder(request);

    assertThat(response.getDiscountAmount()).isZero();
}
```

---

#### 3. 비즈니스 규칙 철저한 검증

✅ **복잡한 계산 검증**:
```java
@Test
void 주문_금액_계산_정확성() {
    // Given
    CreateOrderRequest request = new CreateOrderRequest(
        "U001",
        List.of(
            new OrderItemRequest("P001", 2),  // 890,000 * 2 = 1,780,000
            new OrderItemRequest("P002", 3)   // 120,000 * 3 = 360,000
        ),
        "COUPON_10"  // 10% 할인
    );

    // When
    OrderResponse response = orderService.createOrder(request);

    // Then - 모든 금액 검증
    assertThat(response.getSubtotalAmount()).isEqualTo(2_140_000L);  // 1,780,000 + 360,000
    assertThat(response.getDiscountAmount()).isEqualTo(214_000L);    // 2,140,000 * 10%
    assertThat(response.getTotalAmount()).isEqualTo(1_926_000L);     // 2,140,000 - 214,000
}
```

✅ **상태 전이 검증**:
```java
@Test
void 주문_상태_전이_검증() {
    // Given
    CreateOrderRequest request = new CreateOrderRequest("U001", items, null);
    OrderResponse order = orderService.createOrder(request);

    // 초기 상태: PENDING
    assertThat(order.getStatus()).isEqualTo("PENDING");

    // When: 결제 처리
    PaymentResponse payment = orderService.processPayment(order.getOrderId(), new PaymentRequest("U001"));

    // Then: 상태 COMPLETED로 변경
    assertThat(payment.getStatus()).isEqualTo("SUCCESS");

    Order completedOrder = orderRepository.findById(order.getOrderId()).orElseThrow();
    assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(completedOrder.getPaidAt()).isNotNull();
}
```

---

### 테스트 격리 전략 (Test Isolation)

#### 문제: 테스트 간 데이터 공유로 인한 실패

```java
// ❌ 나쁜 예시: 테스트 간 간섭
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    void 테스트1() {
        orderService.createOrder(...);
        // DB에 주문 저장
    }

    @Test
    void 테스트2() {
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);  // ❌ 테스트1 실행 여부에 따라 실패!
    }
}
```

---

#### 해결책 1: Superclass 패턴

```java
@SpringBootTest
@Transactional  // 각 테스트 후 자동 롤백
public abstract class IntegrationTestSupport {

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @BeforeEach
    void setUpCommon() {
        // 공통 테스트 데이터 초기화
        initTestData();
    }

    @AfterEach
    void tearDownCommon() {
        // 테스트 후 데이터 정리 (@Transactional로 자동 롤백됨)
    }

    protected void initTestData() {
        // 기본 테스트 데이터
        Product product = Product.create("P001", "노트북", "설명", 890000L, "전자제품", 10);
        productRepository.save(product);

        User user = User.create("U001", "test@example.com", "테스트유저");
        user.charge(1000000L);
        userRepository.save(user);
    }
}

// 사용
class OrderIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderService orderService;

    @Test
    void 주문_생성_성공() {
        // 공통 데이터 자동 로드됨
        CreateOrderRequest request = new CreateOrderRequest("U001", items, null);

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
    }

    @Test
    void 주문_조회_성공() {
        // 테스트 격리: 테스트1의 데이터는 롤백됨
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).isEmpty();  // ✅ 항상 성공
    }
}
```

---

#### 해결책 2: Custom Annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public @interface IntegrationTest {
}

// 사용
@IntegrationTest  // 한 줄로 간결!
class OrderIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    void 주문_생성_성공() {
        // 테스트 코드
    }
}
```

---

#### 해결책 3: @DirtiesContext (비추천)

```java
@SpringBootTest
class OrderServiceTest {

    @DirtiesContext  // ❌ 테스트마다 ApplicationContext 재생성 (느림!)
    @Test
    void 테스트1() {
        // ...
    }
}
```

**문제점:**
- ❌ 매우 느림 (Context 재시작)
- ❌ 리소스 낭비

**대안:**
- ✅ `@Transactional` 사용 (빠르고 효율적)

---

### 테스트 품질 평가 기준

| 기준 | 나쁨 ❌ | 좋음 ✅ |
|------|-------|--------|
| **Assertion** | `assertNotNull()` 만 | 구체적인 값 검증 |
| **Edge Case** | 정상 케이스만 | 경계값, Null, Empty 모두 검증 |
| **비즈니스 규칙** | 단순 CRUD 검증 | 계산, 상태 전이 철저히 검증 |
| **격리** | 테스트 간 간섭 | `@Transactional`로 완전 격리 |
| **명명** | `test1()`, `test2()` | `주문_생성_재고_부족_실패()` |
| **Given-When-Then** | 없음 | 명확히 구분 |

---

### 실전 예시: 고품질 테스트

```java
@SpringBootTest
@Transactional
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Given: 테스트 데이터 준비
        User user = User.create("U001", "test@example.com", "테스트유저");
        userRepository.save(user);

        Coupon coupon = new Coupon("C001", "10% 할인", 10, 100);
        couponRepository.save(coupon);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 성공 (정상 케이스)")
    void 쿠폰_발급_성공() {
        // When
        UserCoupon userCoupon = couponService.issueCoupon("U001", "C001");

        // Then: 구체적인 검증
        assertThat(userCoupon).isNotNull();
        assertThat(userCoupon.getUserId()).isEqualTo("U001");
        assertThat(userCoupon.getCouponId()).isEqualTo("C001");
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(userCoupon.getIssuedAt()).isNotNull();

        // 부수 효과 검증
        Coupon coupon = couponRepository.findById("C001").orElseThrow();
        assertThat(coupon.getIssuedQuantity().get()).isEqualTo(1);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 실패 (수량 소진)")
    void 쿠폰_발급_실패_수량소진() {
        // Given: 쿠폰 100개 모두 발급
        Coupon coupon = couponRepository.findById("C001").orElseThrow();
        coupon.getIssuedQuantity().set(100);  // 수량 소진
        couponRepository.save(coupon);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon("U001", "C001"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT)
            .hasMessageContaining("소진");
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 실패 (중복 발급)")
    void 쿠폰_발급_실패_중복() {
        // Given: 이미 발급받음
        couponService.issueCoupon("U001", "C001");

        // When & Then: 두 번째 시도
        assertThatThrownBy(() -> couponService.issueCoupon("U001", "C001"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ISSUED_COUPON);

        // 발급 수량은 1개만
        Coupon coupon = couponRepository.findById("C001").orElseThrow();
        assertThat(coupon.getIssuedQuantity().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - Edge Case (정확히 마지막 1개)")
    void 쿠폰_발급_마지막1개() {
        // Given: 99개 발급됨
        Coupon coupon = couponRepository.findById("C001").orElseThrow();
        coupon.getIssuedQuantity().set(99);
        couponRepository.save(coupon);

        // When: 마지막 1개 발급
        UserCoupon userCoupon = couponService.issueCoupon("U001", "C001");

        // Then: 성공
        assertThat(userCoupon).isNotNull();
        assertThat(coupon.getIssuedQuantity().get()).isEqualTo(100);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);

        // 추가 발급 시도는 실패
        assertThatThrownBy(() -> couponService.issueCoupon("U002", "C001"))
            .isInstanceOf(BusinessException.class);
    }
}
```

---

### 핵심 원칙

1. **AAA 패턴 (Arrange-Act-Assert)**
   - Given: 테스트 데이터 준비
   - When: 실행
   - Then: 검증

2. **F.I.R.S.T 원칙**
   - **Fast**: 빠르게 실행
   - **Independent**: 독립적 (테스트 간 격리)
   - **Repeatable**: 반복 가능 (항상 같은 결과)
   - **Self-Validating**: 자동 검증 (수동 확인 불필요)
   - **Timely**: 적시에 작성 (코드 작성 후 바로)

3. **의미 있는 Assertion**
   - `assertNotNull()` → `assertThat(product.getName()).isEqualTo("노트북")`
   - 구체적인 값 검증

4. **Edge Case 커버리지**
   - 정상 케이스뿐만 아니라 경계값, Null, Empty 모두 테스트

5. **테스트 격리**
   - `@Transactional`로 자동 롤백
   - 테스트 간 데이터 공유 방지

---

## 📚 참고 자료

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Toss 테스트 전략](https://toss.tech/article/test-strategy-server)
- [F.I.R.S.T Principles](https://github.com/ghsukumar/SFDC_Best_Practices/wiki/F.I.R.S.T-Principles-of-Unit-Testing)

## 📚 관련 명령어

- `/week3-guide` - Week 3 전체 가이드
- `/concurrency` - 동시성 테스트 상세
- `/implementation` - 구현 가이드
- `/week3-faq` - FAQ (Q1, Q7, Q8 참고)
