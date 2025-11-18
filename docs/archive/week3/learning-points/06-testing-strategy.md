# 6. 테스트 전략 (Testing Strategy)

## 📌 핵심 개념

**테스트 전략**: 핵심 비즈니스 로직을 완성도 높게, 일반 서비스 코드를 적절히 테스트하는 균형 잡힌 접근

---

## 🎯 테스트 커버리지의 실용적 접근

### 로이코치님 조언
> "핵심 비즈니스 로직은 90%+, 일반 서비스 코드는 70-80%를 목표로 하세요."

### 커버리지 목표

| 코드 유형 | 목표 커버리지 | 예시 |
|----------|--------------|------|
| **핵심 비즈니스** | 90%+ | 재고 차감, 쿠폰 발급, 결제 |
| **일반 서비스** | 70-80% | CRUD, 단순 조회 |
| **Infrastructure** | 선택적 | Repository 구현체 |

### 핵심 비즈니스 로직 파악 방법
1. 도메인 규칙이 포함된 로직 (재고 부족 검증, 쿠폰 수량 제한)
2. 돈/수량이 관련된 로직 (결제, 포인트, 재고)
3. Race Condition이 발생할 수 있는 로직 (선착순 쿠폰)

---

## 🧪 테스트 계층별 전략

### 1. Domain Layer 테스트 (가장 중요)

**특징:**
- ✅ Mock 불필요 (순수 로직)
- ✅ 빠른 실행
- ✅ 비즈니스 규칙 검증

```java
class ProductTest {

    @Test
    void 재고_차감_성공() {
        // Given
        Product product = new Product("P001", "노트북", 10, 890000L);

        // When
        product.decreaseStock(3);

        // Then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void 재고_부족시_예외_발생() {
        // Given
        Product product = new Product("P001", "노트북", 5, 890000L);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void 수량이_0_이하면_예외_발생() {
        // Given
        Product product = new Product("P001", "노트북", 10, 890000L);

        // When & Then
        assertThatThrownBy(() -> product.decreaseStock(0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);

        assertThatThrownBy(() -> product.decreaseStock(-1))
            .isInstanceOf(BusinessException.class);
    }
}
```

---

### 2. Application Layer 테스트 (Mock 활용)

**특징:**
- ✅ Mock Repository 사용
- ✅ 비즈니스 플로우 검증
- ✅ DTO 변환 검증

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
        Product product = new Product(productId, "노트북", 10, 890000L);
        when(productRepository.findById(productId))
            .thenReturn(Optional.of(product));

        // When
        ProductResponse response = productUseCase.getProduct(productId);

        // Then
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getName()).isEqualTo("노트북");
        assertThat(response.getStock()).isEqualTo(10);

        // 행위 검증
        verify(productRepository).findById(productId);
    }

    @Test
    void 상품_없음_예외_발생() {
        // Given
        String productId = "INVALID";
        when(productRepository.findById(productId))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productUseCase.getProduct(productId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productRepository).findById(productId);
    }
}
```

---

### 3. Integration Test (통합 테스트)

**특징:**
- ✅ 실제 Spring Context 로딩 (@SpringBootTest)
- ✅ 여러 계층 통합 검증
- ✅ 동시성 시나리오 테스트

```java
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // 초기 데이터 설정
        Product product = new Product("P001", "노트북", 10, 890000L);
        productRepository.save(product);
    }

    @Test
    void 주문_생성_통합_테스트() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .userId("U001")
            .items(List.of(
                new OrderItemRequest("P001", 2)
            ))
            .build();

        // When
        OrderResponse response = orderUseCase.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getItems()).hasSize(1);

        // 재고 차감 확인
        Product product = productRepository.findById("P001").orElseThrow();
        assertThat(product.getStock()).isEqualTo(8);
    }
}
```

---

## 📊 단위 테스트 vs 통합 테스트

### 비교표

| 항목 | 단위 테스트 | 통합 테스트 |
|------|-----------|-----------|
| **범위** | 단일 클래스 | 여러 계층 |
| **의존성** | Mock 사용 | 실제 객체 |
| **속도** | 빠름 (⚡⚡⚡) | 느림 (⚡) |
| **안정성** | 높음 | 낮음 (환경 의존) |
| **목적** | 로직 검증 | 통합 검증 |

### 권장 비율
```
단위 테스트 : 통합 테스트 = 7 : 3

Domain + Application Layer 단위 테스트: 70%
Integration Test: 30%
```

---

## 🎯 Jacoco로 커버리지 측정

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
    finalizedBy jacocoTestReport  // 테스트 후 리포트 자동 생성
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

### 커버리지 확인
```bash
# 테스트 실행 및 커버리지 측정
./gradlew test jacocoTestReport

# 커버리지 검증 (70% 미만 시 빌드 실패)
./gradlew jacocoTestCoverageVerification

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

---

## 🔍 Mock vs Stub

### 로이코치님 조언
> "Entity + Service 테스트만으로 80-90%는 커버할 것입니다."

### 비교

| 항목 | Mock | Stub |
|------|------|------|
| **목적** | 행위 검증 | 상태 검증 |
| **사용** | `verify()` | `when().thenReturn()` |

### 예시
```java
@Test
void Mock과_Stub의_차이() {
    // Stub: 반환값 설정
    when(productRepository.findById("P001"))
        .thenReturn(Optional.of(product));

    // 실행
    ProductResponse response = productUseCase.getProduct("P001");

    // 상태 검증 (Stub)
    assertThat(response.getProductId()).isEqualTo("P001");

    // 행위 검증 (Mock)
    verify(productRepository).findById("P001");
}
```

---

## 🔒 테스트 격리 전략 (Test Isolation) ⭐

### 코치 피드백
> 테스트 격리 방법은 다양합니다. 테스트 설정을 위한 슈퍼클래스를 만들거나, 어노테이션을 활용하여 초기화 시점에 개입하는 방법을 고려해보세요.

**참고 자료:**
- [Toss - 테스트 전략](https://toss.tech/article/test-strategy-server)

---

### 테스트 격리가 필요한 이유

**문제 상황:**
```java
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 주문_생성_테스트() {
        // 테스트마다 초기 데이터 설정 반복
        Product product = new Product("P001", "노트북", 10, 890000L);
        productRepository.save(product);
        // ...
    }

    @Test
    void 재고_부족_테스트() {
        // 또 다시 동일한 초기 데이터 설정
        Product product = new Product("P001", "노트북", 10, 890000L);
        productRepository.save(product);
        // ...
    }
}
```

**문제점:**
- ❌ 모든 테스트에서 초기 데이터 설정 코드 반복
- ❌ 테스트 간 데이터 오염 가능 (공유 상태)
- ❌ 테스트 순서에 따라 결과가 달라질 수 있음

---

### 방법 1: Superclass Pattern (추천) ⭐

**개념:** 공통 테스트 설정을 슈퍼클래스에 정의하고 상속받아 사용

**장점:**
- ✅ 공통 설정 재사용
- ✅ 테스트 코드 간결화
- ✅ 초기 데이터 중앙 관리

```java
// 공통 슈퍼클래스
@SpringBootTest
@Transactional  // 각 테스트 후 롤백
public abstract class IntegrationTestSupport {

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @BeforeEach
    void setUpCommon() {
        // 모든 테스트에서 사용할 공통 데이터 초기화
        initTestData();
    }

    protected void initTestData() {
        // 상품 데이터
        productRepository.save(
            new Product("P001", "노트북", 10, 890000L, "전자제품")
        );
        productRepository.save(
            new Product("P002", "키보드", 20, 120000L, "주변기기")
        );

        // 사용자 데이터
        userRepository.save(new User("U001", "테스트유저", 1000000L));
    }

    @AfterEach
    void tearDownCommon() {
        // @Transactional이 있으면 자동 롤백되므로 생략 가능
        // 명시적으로 정리하려면:
        // orderRepository.deleteAll();
        // productRepository.deleteAll();
        // userRepository.deleteAll();
    }
}
```

**사용:**
```java
// 실제 테스트 클래스
class OrderIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderUseCase orderUseCase;

    @Test
    void 주문_생성_성공() {
        // Given - 공통 데이터가 이미 준비됨
        CreateOrderRequest request = CreateOrderRequest.builder()
            .userId("U001")
            .items(List.of(new OrderItemRequest("P001", 2)))
            .build();

        // When
        OrderResponse response = orderUseCase.createOrder(request);

        // Then
        assertThat(response.getOrderId()).isNotNull();
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void 재고_부족_예외_발생() {
        // Given - 공통 데이터 활용
        CreateOrderRequest request = CreateOrderRequest.builder()
            .userId("U001")
            .items(List.of(new OrderItemRequest("P001", 100)))  // 재고 10 < 요청 100
            .build();

        // When & Then
        assertThatThrownBy(() -> orderUseCase.createOrder(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
    }
}
```

---

### 방법 2: Custom Annotation

**개념:** 커스텀 어노테이션으로 테스트 설정을 묶어서 재사용

**장점:**
- ✅ 선언적 설정 (코드가 간결)
- ✅ 여러 어노테이션을 하나로 묶음
- ✅ 유연한 조합 가능

```java
// 커스텀 어노테이션 정의
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // 클래스당 한 번만 인스턴스 생성
public @interface IntegrationTest {
}
```

**사용:**
```java
@IntegrationTest  // 한 줄로 모든 설정 완료
class OrderIntegrationTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    @BeforeAll
    void setUp() {
        // 초기 데이터 설정
        productRepository.save(
            new Product("P001", "노트북", 10, 890000L)
        );
    }

    @Test
    void 주문_생성_성공() {
        // 테스트 코드
    }
}
```

**고급 예시:**
```java
// 여러 환경에 맞는 어노테이션 정의
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Transactional
@ActiveProfiles("test")  // 테스트 프로파일 활성화
public @interface WebIntegrationTest {
}

// 사용
@WebIntegrationTest
class ProductControllerTest {
    // ...
}
```

---

### 방법 3: TestContainers (고급)

**개념:** Docker 컨테이너를 활용하여 실제 데이터베이스 환경에서 테스트

**장점:**
- ✅ 실제 DB 환경과 동일한 테스트
- ✅ H2와 MySQL의 차이점 해소
- ✅ 완전한 격리 (컨테이너마다 독립적)

**단점:**
- ❌ Docker 필요
- ❌ 테스트 실행 시간 증가
- ❌ 설정 복잡도 증가

```gradle
// build.gradle
dependencies {
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:mysql:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
}
```

```java
// TestContainers 슈퍼클래스
@Testcontainers
@SpringBootTest
public abstract class ContainerTestSupport {

    // MySQL 컨테이너 (테스트마다 새로 시작)
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    // Spring에 컨테이너 정보 주입
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    protected ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // 초기 데이터 설정
        productRepository.save(
            new Product("P001", "노트북", 10, 890000L)
        );
    }
}
```

**사용:**
```java
class OrderIntegrationTest extends ContainerTestSupport {

    @Test
    void 주문_생성_성공() {
        // 실제 MySQL 컨테이너에서 테스트
    }
}
```

---

### 방법 4: @Sql을 활용한 데이터 초기화

**개념:** SQL 파일로 테스트 데이터를 관리

**장점:**
- ✅ SQL로 데이터 정의 (명확)
- ✅ 복잡한 데이터 관계 표현 용이
- ✅ 파일로 관리하여 재사용

```sql
-- src/test/resources/test-data.sql
INSERT INTO products (id, name, stock, price, category)
VALUES ('P001', '노트북', 10, 890000, '전자제품');

INSERT INTO users (id, name, point)
VALUES ('U001', '테스트유저', 1000000);
```

```java
@SpringBootTest
@Transactional
@Sql("/test-data.sql")  // 테스트 전 SQL 실행
class OrderIntegrationTest {

    @Test
    void 주문_생성_성공() {
        // test-data.sql의 데이터가 이미 준비됨
    }
}

// 특정 테스트만 다른 데이터 사용
@SpringBootTest
@Transactional
class ProductSearchTest {

    @Test
    @Sql("/product-search-data.sql")  // 메서드 레벨 적용
    void 상품_검색_테스트() {
        // ...
    }
}
```

---

### 비교 및 선택 가이드

| 방법 | 복잡도 | 재사용성 | 실행 속도 | Week 3-4 추천 |
|------|--------|---------|----------|--------------|
| **Superclass Pattern** | 낮음 | 높음 | 빠름 | ⭐ 가장 추천 |
| **Custom Annotation** | 중간 | 높음 | 빠름 | ✅ 권장 |
| **@Sql** | 낮음 | 중간 | 빠름 | ✅ 권장 |
| **TestContainers** | 높음 | 높음 | 느림 | △ Week 5 이후 |

---

### Week 3-4 추천: Superclass Pattern + @Transactional

**이유:**
1. ✅ 구현이 간단하고 직관적
2. ✅ @Transactional로 자동 롤백 (격리 보장)
3. ✅ 공통 Repository를 슈퍼클래스에 정의하여 재사용
4. ✅ 초기 데이터 설정을 중앙에서 관리

**템플릿 코드:**
```java
// IntegrationTestSupport.java
@SpringBootTest
@Transactional
public abstract class IntegrationTestSupport {

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected UserRepository userRepository;

    @BeforeEach
    void setUpCommon() {
        initTestData();
    }

    protected void initTestData() {
        // 기본 테스트 데이터
        productRepository.save(Product.create("P001", "노트북", 10, 890000L, "전자제품"));
        userRepository.save(User.create("U001", "테스트유저", 1000000L));
    }

    // 자식 클래스에서 추가 데이터 설정 가능
    protected void addTestData() {
        // Override 가능
    }
}

// 사용
class OrderIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderUseCase orderUseCase;

    @Test
    void 주문_생성_성공() {
        // 공통 데이터 활용
    }

    @Override
    protected void addTestData() {
        // 이 테스트에만 필요한 추가 데이터
        productRepository.save(
            Product.create("P999", "특별상품", 5, 500000L, "한정판")
        );
    }
}
```

---

### 참고: Toss 테스트 전략

**핵심 원칙:**
1. **F.I.R.S.T 원칙**
   - Fast: 빠르게 실행
   - Isolated: 독립적 실행
   - Repeatable: 반복 가능
   - Self-validating: 자체 검증
   - Timely: 적시 작성

2. **테스트 격리 보장**
   - 각 테스트는 독립적으로 실행 가능해야 함
   - @Transactional 또는 @DirtiesContext 활용

3. **공통 설정 추출**
   - 슈퍼클래스 또는 Fixture 클래스 활용
   - 테스트 코드의 중복 제거

**더 자세한 내용:** https://toss.tech/article/test-strategy-server

---

## ✅ Pass 기준

### 테스트 커버리지
- [ ] 전체 커버리지 70% 이상
- [ ] Domain Layer 90% 이상
- [ ] Application Layer 80% 이상

### 테스트 품질
- [ ] 단위 테스트와 통합 테스트 균형
- [ ] 핵심 비즈니스 로직 완전 검증
- [ ] Mock을 활용한 격리된 테스트

### 코드 품질
- [ ] Given-When-Then 패턴 사용
- [ ] 테스트 메서드명이 명확 (한글 OK)
- [ ] Arrange-Act-Assert 분리

---

## ❌ Fail 사유

### 테스트 Fail
- ❌ 테스트 부재 (0%)
- ❌ 낮은 커버리지 (50% 미만)
- ❌ 통합 테스트만 존재 (단위 테스트 누락)

### 품질 Fail
- ❌ 의미 없는 테스트 (커버리지 맞추기용)
- ❌ 테스트 메서드명이 불명확
- ❌ 검증 누락 (assertThat 없음)

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] 단위 테스트와 통합 테스트의 차이를 설명할 수 있다
- [ ] Mock과 Stub의 차이를 설명할 수 있다
- [ ] 테스트 커버리지 70%의 의미를 설명할 수 있다

### 실전 적용
- [ ] Domain Layer 단위 테스트를 작성할 수 있다
- [ ] Mock을 활용한 UseCase 테스트를 작성할 수 있다
- [ ] Jacoco로 커버리지를 측정할 수 있다

### 토론 주제
- "Domain Layer 테스트에서 Mock이 필요한가요?"
- "통합 테스트와 단위 테스트의 비율은 어떻게 가져갔나요?"
- "커버리지 70%를 달성하기 위해 어떤 전략을 사용했나요?"

---

## 📚 참고 자료

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- CLAUDE.md - Q1. TDD로 개발해야 하나요?

---

## 💡 실전 팁

### 70% 달성 전략
```
우선순위:
1. Domain Layer (Entity 메서드) - 필수
2. Application Layer (UseCase) - 필수
3. Integration Test (핵심 플로우) - 권장
4. Controller - 선택 (통합 테스트로 대체 가능)
5. Repository 구현체 - 선택 (단순 CRUD 생략 가능)
```

### 테스트 메서드명
```java
// ✅ 좋은 예 (한글, 의도 명확)
@Test
void 재고_차감_성공() { }

@Test
void 재고_부족시_예외_발생() { }

// ❌ 나쁜 예 (의도 불명확)
@Test
void test1() { }

@Test
void decreaseStockTest() { }
```

---

**이전 학습**: [05. 동시성 제어](./05-concurrency-control.md)
**다음 학습**: [07. DTO 설계 전략](./07-dto-design.md)
