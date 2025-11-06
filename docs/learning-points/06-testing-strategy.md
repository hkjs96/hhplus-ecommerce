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
