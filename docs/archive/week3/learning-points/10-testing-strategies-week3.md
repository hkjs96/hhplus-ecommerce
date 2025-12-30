# 10. Week 3 테스트 전략 (Testing Strategies)

## 📌 핵심 개념

**Week 3 특수성**: In-Memory Repository 사용 → Mock이 필요 없다!

---

## 🎯 3가지 테스트 방식 비교

### 방식 1: Mock 사용 (@Mock)

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void 상품_조회_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자", 10);
        when(productRepository.findById("P001"))
            .thenReturn(Optional.of(product));

        // When
        ProductResponse response = productService.getProduct("P001");

        // Then
        assertThat(response.getProductId()).isEqualTo("P001");
        verify(productRepository).findById("P001");
    }
}
```

**장점:**
- ✅ 완전히 격리된 단위 테스트
- ✅ Repository 구현체 없이도 테스트 가능
- ✅ 빠른 실행 속도
- ✅ 행위 검증 가능 (`verify()`)

**단점:**
- ❌ **Week 3에서 과도함**: In-Memory Repository가 이미 빠름
- ❌ **실제 동작 검증 불가**: Mock은 가짜 객체
- ❌ **ConcurrentHashMap 동작 미검증**: Thread-safety 확인 불가
- ❌ Setup 코드 증가 (`when().thenReturn()`)

**Week 3 적합성**: ❌ **비추천** (In-Memory인데 굳이 Mock?)

---

### 방식 2: @SpringBootTest (통합 테스트)

```java
@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // Repository 초기화 (선택적)
    }

    @Test
    void 상품_조회_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자", 10);
        productRepository.save(product);

        // When
        ProductResponse response = productService.getProduct("P001");

        // Then
        assertThat(response.getProductId()).isEqualTo("P001");
        assertThat(response.getName()).isEqualTo("노트북");
    }
}
```

**장점:**
- ✅ **실제 Spring Bean 사용**: 자동 주입, 설정 반영
- ✅ **실전과 동일한 환경**: 프로덕션과 가장 가까움
- ✅ **실제 Repository 검증**: ConcurrentHashMap 동작 확인
- ✅ **여러 Layer 통합 검증**: Service + Repository 함께 테스트

**단점:**
- ❌ **느림**: Spring ApplicationContext 로딩 (~2-5초)
- ❌ **무거움**: 모든 Bean 초기화
- ❌ **테스트 격리 어려움**: 다른 Bean의 영향 받을 수 있음
- ❌ **단위 테스트가 아님**: 통합 테스트에 가까움

**Week 3 적합성**: △ **조건부 추천** (통합 테스트용으로는 좋음)

**사용 시기:**
- 전체 플로우 검증 (Controller → Service → Repository)
- 동시성 테스트 (200명 동시 요청 → 100개 발급)
- Spring 설정 검증

---

### 방식 3: Repository 직접 생성 (⭐ Week 3 권장)

```java
class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // Repository 직접 생성
        productRepository = new InMemoryProductRepository();

        // Service 직접 생성 (수동 주입)
        productService = new ProductService(productRepository);
    }

    @AfterEach
    void tearDown() {
        // Repository 초기화 (다음 테스트를 위해)
        // InMemoryProductRepository는 새로 생성되므로 불필요
    }

    @Test
    void 상품_조회_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자", 10);
        productRepository.save(product);

        // When
        ProductResponse response = productService.getProduct("P001");

        // Then
        assertThat(response.getProductId()).isEqualTo("P001");
        assertThat(response.getName()).isEqualTo("노트북");
        assertThat(response.getPrice()).isEqualTo(890000L);

        // 실제 Repository에서 조회 확인 가능
        Product saved = productRepository.findById("P001").orElseThrow();
        assertThat(saved.getName()).isEqualTo("노트북");
    }

    @Test
    void 상품_조회_실패_존재하지않는상품() {
        // Given: Repository에 아무것도 없음

        // When & Then
        assertThatThrownBy(() -> productService.getProduct("INVALID"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 여러_상품_저장_후_조회() {
        // Given
        Product p1 = Product.create("P001", "노트북", "고성능", 890000L, "전자", 10);
        Product p2 = Product.create("P002", "키보드", "기계식", 120000L, "주변", 50);
        productRepository.save(p1);
        productRepository.save(p2);

        // When
        ProductListResponse response = productService.getProducts(null, null);

        // Then
        assertThat(response.getProducts()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(2);
    }
}
```

**장점:**
- ✅ **빠름**: Spring Context 로딩 없음 (~0.1초)
- ✅ **실제 Repository 검증**: ConcurrentHashMap 동작 확인
- ✅ **격리된 테스트**: 각 테스트마다 새 Repository 생성
- ✅ **간단한 Setup**: `new InMemoryProductRepository()`만 하면 됨
- ✅ **Thread-safety 검증 가능**: 실제 ConcurrentHashMap 사용
- ✅ **Week 3 특성 활용**: In-Memory의 빠른 속도 그대로

**단점:**
- ❌ **수동 주입**: `new` 키워드로 직접 생성
- ❌ **Spring 기능 미사용**: @Autowired 없음
- ❌ **의존성 변경 시 수정**: 생성자 파라미터 변경 시 테스트도 수정

**Week 3 적합성**: ✅ ⭐ **강력 추천**

**이유:**
1. In-Memory는 이미 빠르므로 Mock 불필요
2. 실제 ConcurrentHashMap 동작 검증 가능
3. Spring Context 로딩 오버헤드 없음
4. 단위 테스트 수준의 속도 + 통합 테스트 수준의 실전성

---

## 📊 3가지 방식 종합 비교

| 항목 | Mock (@Mock) | SpringBoot (@SpringBootTest) | **직접 생성 (Week 3 권장)** |
|------|-------------|------------------------------|---------------------------|
| **실행 속도** | ⚡⚡⚡ (0.05s) | ⚡ (2-5s) | ⚡⚡⚡ (0.1s) |
| **격리성** | ✅ 완전 격리 | △ Bean 간섭 | ✅ 완전 격리 |
| **실제 동작 검증** | ❌ Mock | ✅ 실제 Bean | ✅ 실제 Repository |
| **ConcurrentHashMap 검증** | ❌ 불가능 | ✅ 가능 | ✅ 가능 |
| **Setup 복잡도** | when().thenReturn() | @Autowired | new XXXRepository() |
| **Spring 의존성** | ❌ 없음 | ✅ 필요 | ❌ 없음 |
| **테스트 레벨** | 순수 단위 | 통합 | 단위 + 실전 |
| **Week 3 적합성** | ❌ 과도함 | △ 통합 테스트용 | ✅ **최적** |

---

## 🎯 Week 3 권장 전략

### Application Layer 테스트: 방식 3 (Repository 직접 생성)

```java
class ProductServiceTest {
    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productService = new ProductService(productRepository);
    }
}
```

**이유:**
- In-Memory Repository는 빠르므로 Mock 불필요
- 실제 ConcurrentHashMap 동작 검증 가능
- Spring 없이도 빠른 테스트

---

### Domain Layer 테스트: Pure Java (변화 없음)

```java
class ProductTest {
    @Test
    void 재고_차감_성공() {
        // Given
        Product product = Product.create("P001", "노트북", "고성능", 890000L, "전자", 10);

        // When
        product.decreaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(7);
    }
}
```

**이유:**
- 외부 의존성 없는 순수 로직
- Mock 불필요
- 가장 빠름

---

### 통합 테스트: @SpringBootTest (선착순 쿠폰 동시성)

```java
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void 선착순_쿠폰_동시성_테스트() throws InterruptedException {
        // Given: 쿠폰 100개
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100,
            LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        couponRepository.save(coupon);

        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 200명이 동시 요청
        for (int i = 0; i < threadCount; i++) {
            String userId = "U" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon("C001", new IssueCouponRequest(userId));
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

        // Then: 정확히 100개만 발급
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);
    }
}
```

**이유:**
- 실제 Spring Bean 사용 (동시성 제어 검증)
- ExecutorService 동시 실행
- Step 6 핵심 검증

---

## 🔍 실전 예시: 3가지 방식으로 같은 테스트 작성

### 테스트 시나리오: "사용자 포인트 충전"

#### 방식 1: Mock 사용

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    @Test
    void 포인트_충전_성공() {
        // Given
        User user = User.create("U001", "test@example.com", "김항해");
        when(userRepository.findById("U001")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        ChargeBalanceResponse response = userService.chargeBalance("U001",
            new ChargeBalanceRequest(500000L));

        // Then
        assertThat(response.getBalance()).isEqualTo(500000L);
        verify(userRepository).findById("U001");
        verify(userRepository).save(user);
    }
}
```

**문제점:**
- Mock 설정이 복잡 (`when().thenReturn()`)
- 실제 Repository 동작 미검증
- Week 3에서 불필요

---

#### 방식 2: @SpringBootTest

```java
@SpringBootTest
class UserServiceTest {
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;

    @Test
    void 포인트_충전_성공() {
        // Given
        User user = User.create("U001", "test@example.com", "김항해");
        userRepository.save(user);

        // When
        ChargeBalanceResponse response = userService.chargeBalance("U001",
            new ChargeBalanceRequest(500000L));

        // Then
        assertThat(response.getBalance()).isEqualTo(500000L);

        // Repository에서 직접 확인
        User saved = userRepository.findById("U001").orElseThrow();
        assertThat(saved.getBalance()).isEqualTo(500000L);
    }
}
```

**장점:**
- 실제 Spring Bean 사용
- 실제 동작 검증

**단점:**
- Spring Context 로딩 (~2-5초)

---

#### 방식 3: Repository 직접 생성 ⭐

```java
class UserServiceTest {
    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        userService = new UserService(userRepository);
    }

    @Test
    void 포인트_충전_성공() {
        // Given
        User user = User.create("U001", "test@example.com", "김항해");
        userRepository.save(user);

        // When
        ChargeBalanceResponse response = userService.chargeBalance("U001",
            new ChargeBalanceRequest(500000L));

        // Then
        assertThat(response.getBalance()).isEqualTo(500000L);

        // Repository에서 직접 확인 (실제 ConcurrentHashMap)
        User saved = userRepository.findById("U001").orElseThrow();
        assertThat(saved.getBalance()).isEqualTo(500000L);
    }
}
```

**장점:**
- 빠름 (~0.1초)
- 실제 ConcurrentHashMap 검증
- Setup 간단

**Week 3 최적!** ✅

---

## ✅ Week 3 테스트 전략 요약

### 1. Domain Layer (Entity 테스트)
- **방식**: Pure Java (외부 의존성 없음)
- **속도**: ⚡⚡⚡
- **예시**: ProductTest, UserTest, CouponTest

### 2. Application Layer (Service 테스트)
- **방식**: Repository 직접 생성 ⭐
- **속도**: ⚡⚡⚡
- **예시**: ProductServiceTest, UserServiceTest, CouponServiceTest

### 3. Integration Test (통합 테스트)
- **방식**: @SpringBootTest
- **속도**: ⚡
- **예시**: CouponConcurrencyTest (Step 6 동시성)

---

## 🚫 안티패턴 (Anti-patterns)

### ❌ Week 3에서 Mock 과다 사용

```java
// ❌ 나쁜 예: In-Memory인데 Mock 사용
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository productRepository;  // 불필요!
    @InjectMocks private ProductService productService;
}
```

**문제점:**
- In-Memory Repository는 이미 빠름
- 실제 ConcurrentHashMap 동작 검증 불가
- when().thenReturn() 설정 코드 과다

**해결:**
```java
// ✅ 좋은 예: Repository 직접 생성
class ProductServiceTest {
    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();  // 실제 사용!
        productService = new ProductService(productRepository);
    }
}
```

---

### ❌ 단위 테스트에 @SpringBootTest 사용

```java
// ❌ 나쁜 예: 간단한 Service 테스트에 Spring 로딩
@SpringBootTest
class ProductServiceTest {
    @Autowired private ProductService productService;

    @Test
    void 상품_조회() {
        // 간단한 조회 테스트인데 Spring 전체 로딩...
    }
}
```

**문제점:**
- 단위 테스트가 느려짐 (2-5초)
- 불필요한 Bean 초기화

**해결:**
```java
// ✅ 좋은 예: Repository 직접 생성으로 빠르게
class ProductServiceTest {
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(new InMemoryProductRepository());
    }
}
```

---

## 💡 실전 팁

### 1. 테스트 속도 비교

```bash
# Mock 사용
./gradlew test --tests ProductServiceTest
> Task :test (0.2s)  ⚡⚡⚡

# @SpringBootTest
./gradlew test --tests ProductServiceIntegrationTest
> Task :test (3.5s)  ⚡

# Repository 직접 생성
./gradlew test --tests ProductServiceTest
> Task :test (0.3s)  ⚡⚡⚡
```

### 2. @BeforeEach로 Repository 초기화

```java
class ProductServiceTest {
    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // 매 테스트마다 새 Repository 생성 (격리)
        productRepository = new InMemoryProductRepository();
        productService = new ProductService(productRepository);
    }

    // 각 테스트는 깨끗한 상태에서 시작
}
```

### 3. 여러 Repository 의존 시

```java
class CouponServiceTest {
    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private UserRepository userRepository;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
        userCouponRepository = new InMemoryUserCouponRepository();
        userRepository = new InMemoryUserRepository();

        couponService = new CouponService(
            couponRepository,
            userCouponRepository,
            userRepository
        );
    }
}
```

---

## 📚 참고 자료

### Week 3 학습 문서
- [06. 테스트 전략](./06-testing-strategy.md) - 일반적인 테스트 전략
- [05. 동시성 제어](./05-concurrency-control.md) - Step 6 동시성 테스트

### 외부 자료
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)

---

## 🎯 Week 3 테스트 체크리스트

### Application Layer 테스트
- [ ] Repository 직접 생성 방식 사용
- [ ] @BeforeEach로 매 테스트 초기화
- [ ] 실제 ConcurrentHashMap 동작 검증
- [ ] 예외 케이스 완전 검증
- [ ] Given-When-Then 패턴 준수

### 통합 테스트
- [ ] @SpringBootTest 사용
- [ ] 동시성 테스트 작성 (ExecutorService + CountDownLatch)
- [ ] Step 6: 200명 요청 → 100개 발급 검증

### 피해야 할 것
- [ ] In-Memory인데 Mock 사용 ❌
- [ ] 단순 테스트에 @SpringBootTest ❌
- [ ] 테스트 간 상태 공유 ❌

---

**이전 학습**: [09. Thread-Safe 컬렉션](./09-concurrent-collections.md)
**다음 학습**: [README](../README.md)
