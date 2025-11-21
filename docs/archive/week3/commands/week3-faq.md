---
description: Week 3 (Step 5-6) 자주 묻는 질문 (FAQ)
---

# Week 3 FAQ

> Week 3 과제 진행 중 자주 나오는 질문들을 정리했습니다.

## ❓ FAQ (자주 묻는 질문)

### Q1. TDD로 개발해야 하나요?
**A:** TDD는 권장사항이지만 필수는 아닙니다.
- ✅ **테스트 커버리지 70% 이상**이 핵심 평가 기준입니다.
- ✅ 구현 후 테스트를 작성해도 무방합니다.
- 💡 TDD를 시도해보면 설계 개선에 도움이 됩니다.

**TDD 프로세스 (선택):**
1. 실패하는 테스트 작성 (Red)
2. 최소한의 코드로 테스트 통과 (Green)
3. 리팩토링 (Refactor)

**테스트 커버리지의 실용적 접근 (로이코치님 조언):**
- 🎯 **핵심 비즈니스 로직**: 완성도 최대화 (90%+ 목표)
  - 예: 재고 차감, 쿠폰 발급, 결제 처리
- ⚖️ **일반 서비스 코드**: 적절한 수준 (70-80%)
  - 예: CRUD, 단순 조회 로직
- ⚠️ **주의**: 테스트 커버리지에 맞추려다 의미 없는 테스트를 작성하지 말 것

**핵심 비즈니스 로직 파악 방법:**
1. 도메인 규칙이 포함된 로직 (재고 부족 검증, 쿠폰 수량 제한)
2. 돈/수량이 관련된 로직 (결제, 포인트, 재고)
3. Race Condition이 발생할 수 있는 로직 (선착순 쿠폰)

---

### Q2. 의존성 주입(DI)을 직접 구현해야 하나요?
**A:** 아니요, Spring의 DI를 사용하세요.
- ✅ `@RequiredArgsConstructor` (Lombok) 사용 권장
- ✅ 생성자 주입 방식 사용
- ❌ 필드 주입(`@Autowired`)은 테스트하기 어려움

**올바른 DI 예시:**
```java
@Service
@RequiredArgsConstructor  // Lombok이 생성자 자동 생성
public class ProductUseCase {
    private final ProductRepository productRepository;  // final로 선언
    // 생성자 자동 생성됨
}
```

---

### Q3. UseCase란 무엇인가요?
**A:** 사용자가 특정 목표를 달성하기 위해 시스템과 상호작용하는 완전한 시나리오입니다.

**UseCase의 본질 (로이코치님 조언):**
- 📋 **유즈케이스 = 요구사항의 단위** (아키텍처 패턴과 무관)
- 🎯 단순히 "상품 조회"가 아니라 "고객이 구매 결정을 내리기 위한 모든 정보 제공"
- 🔄 여러 도메인을 조합하여 완전한 비즈니스 플로우 구성

**실제 예시: 상품 상세 조회 UseCase**
```java
@Service
@RequiredArgsConstructor
public class ProductDetailUseCase {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final StockRepository stockRepository;
    private final ShippingRepository shippingRepository;

    public ProductDetailResponse getProductDetail(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 재고 정보 조회
        Integer stockQuantity = stockRepository.getAvailableStock(productId);

        // 평점/리뷰 통계
        ReviewStats stats = reviewRepository.getStatsByProduct(productId);

        // 배송 예정일 계산
        LocalDate estimatedDelivery = shippingRepository.calculateDeliveryDate(productId);

        // 추천 상품 조회
        List<Product> recommendations = productRepository.findRecommendations(productId);

        return ProductDetailResponse.of(
            product,
            stockQuantity,
            stats,
            estimatedDelivery,
            recommendations
        );
    }
}
```

**중요:**
- ❌ 단순 CRUD가 아니라 완전한 비즈니스 시나리오
- ✅ API 명세를 유스케이스로 구현 (1 API = 1 UseCase 메서드)
- ✅ 코드는 Service가 아니라 **UseCase 클래스**로 작성

---

### Q4. DomainService와 UseCase의 차이는 무엇인가요?
**A:** 역할과 위치가 다릅니다.

| 항목 | DomainService | UseCase |
|------|--------------|---------|
| **위치** | Domain Layer | Application Layer |
| **역할** | 여러 Entity를 조합한 도메인 로직 | API 요청을 처리하는 워크플로우 |
| **예시** | `OrderService.validateOrder()` | `OrderUseCase.createOrder()` |
| **의존성** | Entity, Value Object만 의존 | DomainService, Repository 의존 |

**예시:**
```java
// DomainService (Domain Layer)
@Service
public class OrderService {
    public void validateOrder(Order order, List<Product> products) {
        // 도메인 규칙 검증
    }
}

// UseCase (Application Layer)
@Service
public class OrderUseCase {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;  // DomainService 사용

    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 데이터 조회 (Repository)
        // 2. 비즈니스 로직 (DomainService)
        // 3. 데이터 저장 (Repository)
        // 4. DTO 변환
    }
}
```

---

### Q5. Anemic Domain Model은 무엇인가요?
**A:** 비즈니스 로직 없이 getter/setter만 있는 Entity를 말합니다.

**Anemic (나쁨) ❌:**
```java
public class Product {
    private String id;
    private Integer stock;

    // getter/setter만 존재
}

// Service에 비즈니스 로직
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new Exception("재고 부족");
        }
        product.setStock(product.getStock() - quantity);
    }
}
```

**Rich Domain Model (좋음) ✅:**
```java
public class Product {
    private String id;
    private Integer stock;

    // 비즈니스 로직을 Entity 내부에 캡슐화
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException("재고 부족");
        }
        this.stock -= quantity;
    }
}

// Service는 단순히 호출만
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);  // Entity의 메서드 호출
    }
}
```

---

### Q6. Entity에 Lombok을 사용해도 되나요?
**A:** 네, 사용 권장합니다.
- ✅ `@Getter`: getter 자동 생성
- ✅ `@AllArgsConstructor`: 모든 필드를 받는 생성자 생성
- ❌ `@Setter`: 사용 지양 (불변성을 위해)
- ❌ `@Data`: 너무 많은 기능 포함 (지양)

**권장 사용법:**
```java
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private Integer stock;

    // setter 대신 비즈니스 메서드 제공
    public void decreaseStock(int quantity) {
        this.stock -= quantity;
    }
}
```

---

### Q7. 테스트 커버리지 70%는 어떻게 계산하나요?
**A:** Jacoco로 자동 계산합니다.
```bash
# 테스트 실행 및 커버리지 측정
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

**커버리지 계산 기준:**
- **라인 커버리지**: 전체 코드 라인 대비 실행된 라인 비율
- **브랜치 커버리지**: if/else 분기 실행 비율

**70% 달성 팁:**
- Domain Layer (Entity 메서드) 테스트: 필수
- Application Layer (UseCase) 테스트: 필수
- Infrastructure Layer (Repository): 선택 (단순 CRUD는 생략 가능)
- Presentation Layer (Controller): 선택 (통합 테스트로 대체 가능)

---

### Q8. Mock과 Stub의 차이는 무엇인가요?
**A:** 검증 방식이 다릅니다.

| 항목 | Mock | Stub |
|------|------|------|
| **목적** | 행위 검증 (메서드 호출 확인) | 상태 검증 (반환값 확인) |
| **사용** | `verify()` 사용 | `when().thenReturn()` 사용 |

**예시:**
```java
@Test
void 상품_조회_성공() {
    // Stub: 반환값 설정
    when(productRepository.findById("P001"))
        .thenReturn(Optional.of(product));

    // 실행
    ProductResponse response = productUseCase.getProduct("P001");

    // 상태 검증
    assertThat(response.getProductId()).isEqualTo("P001");

    // Mock: 행위 검증
    verify(productRepository).findById("P001");
}
```

---

### Q9. ConcurrentHashMap과 synchronized 중 어떤 것을 사용해야 하나요?
**A:** 상황에 따라 다릅니다.

| 방식 | 장점 | 단점 | 사용 시기 |
|------|------|------|----------|
| **ConcurrentHashMap** | 높은 동시성, Lock-free | 복잡한 연산 불가 | 단순 CRUD |
| **synchronized** | 간단한 구현 | 전체 메서드 잠금 | 간단한 비즈니스 로직 |
| **AtomicInteger** | 가장 빠름, Lock-free | 단순 증감만 가능 | 카운터, 수량 관리 |

**권장:**
- **Repository (데이터 저장)**: ConcurrentHashMap 사용
- **쿠폰 발급 (수량 제어)**: AtomicInteger + CAS 사용

---

### Q10. 인기 상품 집계를 매번 계산하는 것이 비효율적이지 않나요?
**A:** Week 3에서는 단순 구현이 목표입니다.
- ✅ **초기 구현**: 실시간 쿼리 (매번 계산)
- 🔄 **향후 개선**: 배치 스케줄러 + 캐시 (Week 5)

**Week 3 구현:**
```java
public List<PopularProductResponse> getTopProducts() {
    // 매번 전체 주문을 조회하여 집계 (단순하지만 느림)
    return orderRepository.findAll().stream()
        .filter(order -> order.getCreatedAt().isAfter(threeDaysAgo))
        .flatMap(order -> order.getItems().stream())
        .collect(Collectors.groupingBy(
            OrderItem::getProductId,
            Collectors.summingInt(OrderItem::getQuantity)
        ))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(5)
        .map(this::toResponse)
        .collect(Collectors.toList());
}
```

**Week 5 개선 (참고):**
- 배치 스케줄러: 5분마다 집계
- Redis 캐시: 집계 결과 저장
- Fallback: 캐시 실패 시 실시간 계산

---

### Q11. 레이어별로 DTO를 분리해야 하나요?
**A:** 원칙적으로는 분리하는 것이 맞지만, 실용적으로 접근하세요.

**원칙 (로이코치님 조언):**
- 📌 **레이어별로 관심사와 변경 이유가 다르기 때문에 레이어는 자신만의 DTO를 가져야 함**
- 📌 **소프트웨어 핵심 원칙: 변경 이유가 다른 것은 분리한다**

**실용적 접근:**
- ✅ **도메인 모델이 안정적이면** 여러 레이어에서 사용 가능
- ✅ **Week 3에서는** Domain Entity를 여러 레이어에서 사용해도 무방
- ⚠️ **실무에서는** 레이어별 DTO 분리 권장

**DTO 재사용 전략:**
```java
// 공통 필드를 Composition으로 재사용
public class ProductBaseDto {
    private String productId;
    private String name;
    private Long price;
}

// API별 전용 DTO (단일 책임 원칙)
public class ProductListResponse {
    private ProductBaseDto product;  // 컴포지션
    private Integer stock;
}

public class ProductDetailResponse {
    private ProductBaseDto product;  // 컴포지션
    private List<Review> reviews;
    private Integer avgRating;
}
```

**균형 찾기:**
- 🎯 **단일 책임 원칙 (SRP)**: API마다 전용 DTO
- 🔄 **DRY 원칙**: 공통 부분은 컴포지션으로 재사용
- ⚖️ 두 원칙의 균형을 찾는 것이 중요

---

### Q12. Mock API를 왜 만드나요?
**A:** 협업 시 병목을 줄이고 작업의 병렬성을 높이기 위함입니다.

**Mock API의 목적 (로이코치님 조언):**
1. 🤝 **협업 병목 제거**: 백엔드 완성 전에 프론트/모바일 개발 시작
2. ⚡ **작업 병렬성**: 팀원들이 동시에 작업 가능
3. 🧪 **테스트 가능성**: 가짜 응답 데이터로 UI 테스트

**Week 2 → Week 3 변환 전략:**
```
Week 2 (Mock):
OrderController
  ├── ConcurrentHashMap에 하드코딩된 Mock 데이터
  └── 간단한 CRUD 로직

Week 3 (Layered Architecture):
OrderController                    (Presentation)
  └── OrderUseCase                 (Application)
        ├── OrderService           (Domain)
        ├── ProductRepository      (Domain Interface)
        └── InMemoryOrderRepository (Infrastructure)
```

**중요:**
- ✅ Mock을 잘 정의하고, 이것을 그대로 활용하여 실제 기능으로 전환
- ✅ Controller 이름 유지: `OrderController` (O), `MockOrderController` (X)
- ✅ ConcurrentHashMap을 Repository로 이동시켜 재사용

---

### Q13. Entity에 비즈니스 로직을 두는 이유는 무엇인가요?
**A:** 객체의 능동성, 테스트 용이성, 로직 분산 때문입니다.

**Entity에 로직을 두는 이유 (로이코치님 조언):**
1. 🎯 **객체의 능동성**: Entity가 스스로 행동하도록 (Rich Domain Model)
2. 🧪 **테스트 용이성**: Entity 메서드만 단독으로 테스트 가능
3. 📦 **로직 분산**: Service 로직 간소화 (God Service 방지)

**비교:**
```java
// Anemic Domain Model (❌ 나쁨)
public class Product {
    private Integer stock;
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getStock() { return stock; }
}

@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        // Service에 모든 로직이 집중
        if (product.getStock() < quantity) {
            throw new BusinessException("재고 부족");
        }
        if (quantity <= 0) {
            throw new BusinessException("수량은 0보다 커야 함");
        }
        product.setStock(product.getStock() - quantity);
    }
}

// Rich Domain Model (✅ 좋음)
public class Product {
    private Integer stock;

    // Entity가 스스로 행동 (능동성)
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("수량은 0보다 커야 함");
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException("재고 부족");
        }
    }
}

@Service
public class ProductService {
    public void decreaseStock(Product product, int quantity) {
        product.decreaseStock(quantity);  // 단순 위임
    }
}
```

**테스트 용이성:**
```java
// Entity 메서드만 단독 테스트 (의존성 없음)
@Test
void 재고_차감_성공() {
    Product product = new Product("P001", "노트북", 10);
    product.decreaseStock(3);
    assertThat(product.getStock()).isEqualTo(7);
}
```

---

### Q14. Week 3에서 동시성 제어를 고민해야 하나요?
**A:** Step 5에서는 고민하지 않아도 됩니다. Step 6에서만 고민하세요.

**Week 3 동시성 제어 범위 (로이코치님 조언):**
- ❌ **Step 5**: 동시성 제어 고민 불필요
  - ConcurrentHashMap만으로 충분
  - 레이어드 아키텍처 구현에 집중
- ✅ **Step 6**: 선착순 쿠폰 발급만 동시성 제어
  - synchronized, ReentrantLock, AtomicInteger 중 택1
  - Race Condition 방지 필수

**ConcurrentHashMap 활용:**
```java
@Repository
public class InMemoryProductRepository implements ProductRepository {
    // Thread-safe 컬렉션 (Step 5에서 충분)
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);
        return product;
    }
}
```

---

### Q15. step5와 step6를 하나의 PR로 제출해도 되나요?
**A:** 권장하지 않습니다.
- ✅ **step5 PR**: 레이어드 아키텍처 기본 구현
- ✅ **step6 PR**: step5 기반 위에 동시성 제어 추가

**이유:**
- 리뷰가 용이함 (작은 단위)
- 문제 발생 시 롤백 쉬움
- 점진적 개선 경험

---

### Q16. 입력값 유효성 검증은 어디서 해야 하나요?
**A:** Controller에서 먼저 검증하고, 비즈니스 규칙은 Entity에서 검증하세요.

**검증 레이어 (로이코치님 조언):**
```
입력값 검증 흐름:
Controller > Service > Entity > DB

1. Controller: 형식 검증 (@Valid, @NotNull 등)
2. Entity: 비즈니스 규칙 검증 (재고 부족, 수량 제한 등)
```

**예시:**
```java
// Controller: 형식 검증
@PostMapping("/orders")
public ApiResponse<OrderResponse> createOrder(
    @Valid @RequestBody CreateOrderRequest request  // @Valid로 형식 검증
) {
    return ApiResponse.success(orderUseCase.createOrder(request));
}

// Request DTO: 형식 검증 어노테이션
public class CreateOrderRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다")
    private List<OrderItemRequest> items;
}

// Entity: 비즈니스 규칙 검증
public class Product {
    public void decreaseStock(int quantity) {
        // 비즈니스 규칙 검증
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

**검증 분리 원칙:**
- ✅ Controller: 형식, Null 체크, 범위 검증
- ✅ Entity: 비즈니스 규칙 검증

---

### Q17. Week 3에서 캐시를 구현해야 하나요?
**A:** 아니요, Week 3에서는 캐시를 고민하지 않아도 됩니다.

**이유 (로이코치님 조언):**
- 📌 **Week 3는 인메모리 구현**: DB도 사용하지 않음
- 📌 모든 데이터가 이미 메모리에 있기 때문에 캐시가 불필요
- 📌 캐시는 Week 5 이후 DB 도입 시 고려

**Week 3 Focus:**
- ✅ 레이어드 아키텍처 구현
- ✅ In-Memory Repository (ConcurrentHashMap)
- ✅ 동시성 제어 (Step 6)
- ❌ 캐시 (불필요)

---

### Q18. 유비쿼터스 언어란 무엇인가요?
**A:** 팀원 모두가 사용하는 공통 언어입니다.

**유비쿼터스 언어의 중요성 (로이코치님 조언):**
- 📋 개발자, 기획자, 디자이너가 모두 같은 용어 사용
- 📋 코드에도 동일한 용어 반영
- 📋 커뮤니케이션 비용 감소

**예시:**
```
기획서: "사용자가 상품을 장바구니에 담는다"
↓
코드:
CartUseCase.addItemToCart(userId, productId)  // ✅ 좋음
CartUseCase.insert(userId, productId)         // ❌ 나쁨 (다른 용어)
```

**적용 방법:**
1. 기획서/요구사항의 용어를 그대로 코드에 사용
2. 클래스명, 메서드명, 변수명에 비즈니스 용어 반영
3. 팀 내 용어집 정리 (Glossary)

**예시:**
- "주문" → `Order`, `OrderUseCase`
- "장바구니" → `Cart`, `CartItem`
- "선착순 쿠폰" → `FirstComeCoupon`, `issueCoupon()`

---

## 📚 관련 명령어

- `/week3-guide` - Week 3 전체 가이드
- `/architecture` - 레이어드 아키텍처 상세
- `/concurrency` - 동시성 제어 패턴
- `/testing` - 테스트 전략
- `/implementation` - 구현 가이드 및 코드 예시
