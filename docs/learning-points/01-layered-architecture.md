# 1. 레이어드 아키텍처 (Layered Architecture)

## 📌 핵심 개념

레이어드 아키텍처는 소프트웨어를 계층으로 분리하여 **관심사의 분리(Separation of Concerns)**를 달성하는 아키텍처 패턴입니다.

---

## 🏗️ 4계층 구조

```
┌─────────────────────────────────────┐
│   Presentation Layer (API)          │  Controller, Handler
├─────────────────────────────────────┤
│   Application Layer (UseCase)       │  UseCase, DTO
├─────────────────────────────────────┤
│   Domain Layer (Business Logic)     │  Entity, Repository Interface, DomainService
├─────────────────────────────────────┤
│   Infrastructure Layer (기술 구현)   │  Repository Implementation, External API
└─────────────────────────────────────┘
```

---

## 📋 각 계층의 책임

### 1️⃣ Presentation Layer (표현 계층)
**책임**: HTTP 요청/응답 처리, API 엔드포인트 제공

**주요 역할:**
- HTTP 요청을 받아 UseCase 호출
- UseCase 결과를 HTTP 응답으로 변환
- 입력값 형식 검증 (@Valid)

**포함 클래스:**
- Controller
- Request/Response DTO
- GlobalExceptionHandler

**예시:**
```java
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductUseCase productUseCase;  // Application Layer 의존

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String productId) {
        ProductResponse product = productUseCase.getProduct(productId);
        return ApiResponse.success(product);
    }
}
```

**❌ 하지 말아야 할 것:**
- 비즈니스 로직 작성
- 직접 Repository 호출
- Domain Entity 직접 조작

---

### 2️⃣ Application Layer (응용 계층)
**책임**: 비즈니스 워크플로우 조율, 트랜잭션 관리

**주요 역할:**
- API 명세를 유스케이스로 구현
- 여러 도메인 서비스를 조합하여 완전한 비즈니스 플로우 구성
- DTO 변환 (Domain Entity ↔ Response DTO)

**포함 클래스:**
- Application Service (= UseCase)
- DTO (Request/Response)

> **참고**: 레이어드 아키텍처에서 "Application Service"와 "UseCase"는 같은 개념입니다. Week 3에서는 전통적인 "Service" 네이밍을 사용합니다. (예: `ProductService`, `OrderService`)

**예시:**
```java
@Service
@RequiredArgsConstructor
public class OrderUseCase {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;  // Domain Service

    /**
     * 주문 생성 유스케이스
     * - 상품 조회
     * - 재고 검증
     * - 주문 생성
     * - 재고 차감
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 상품 조회
        List<Product> products = request.getItems().stream()
            .map(item -> productRepository.findById(item.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND)))
            .toList();

        // 2. 재고 검증 (Domain Service)
        orderService.validateStock(products, request.getItems());

        // 3. 주문 생성
        Order order = Order.create(request.getUserId(), request.getItems());
        orderRepository.save(order);

        // 4. 재고 차감
        products.forEach(product ->
            product.decreaseStock(getQuantity(request.getItems(), product.getId()))
        );

        return OrderResponse.from(order);
    }
}
```

**❌ 하지 말아야 할 것:**
- 다른 UseCase 직접 호출 (DomainService 사용)
- Infrastructure 계층 직접 의존
- 도메인 규칙 작성 (Entity에 위임)

---

### 3️⃣ Domain Layer (도메인 계층)
**책임**: 핵심 비즈니스 로직, 도메인 규칙

**주요 역할:**
- 비즈니스 규칙 캡슐화
- 도메인 객체 간의 관계 정의
- Repository 인터페이스 정의

**포함 클래스:**
- Entity (Product, Order, User, Coupon 등)
- Value Object (Money, Quantity 등)
- Repository Interface
- DomainService (여러 Entity를 조합한 로직)

**예시:**
```java
// Entity
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private Integer stock;
    private Long price;

    /**
     * 비즈니스 로직: 재고 차감
     * Domain Layer에서 비즈니스 규칙 검증
     */
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);
        validateStock(quantity);
        this.stock -= quantity;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }

    private void validateStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }
}

// Repository Interface (Domain Layer에 위치)
public interface ProductRepository {
    Optional<Product> findById(String id);
    List<Product> findAll();
    Product save(Product product);
}

// DomainService
@Service
public class OrderService {
    /**
     * 여러 Entity를 조합한 도메인 로직
     */
    public void validateStock(List<Product> products, List<OrderItem> items) {
        for (Product product : products) {
            OrderItem item = findItem(items, product.getId());
            if (!product.hasStock(item.getQuantity())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }
}
```

**❌ 하지 말아야 할 것:**
- Infrastructure 의존 (구현체 직접 사용)
- HTTP, DB 관련 코드
- DTO 사용 (Domain Entity만 사용)

---

### 4️⃣ Infrastructure Layer (인프라 계층)
**책임**: 기술적 구현, 외부 세계와의 통합

**주요 역할:**
- Repository 구현 (In-Memory, JPA 등)
- 외부 API 호출
- 파일 시스템 접근
- 메시지 큐, 캐시 등

**포함 클래스:**
- Repository 구현체
- External API Client
- DataInitializer

**예시:**
```java
@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Product> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public Product save(Product product) {
        storage.put(product.getId(), product);
        return product;
    }
}
```

**❌ 하지 말아야 할 것:**
- 비즈니스 로직 작성
- Domain Entity 조작
- 다른 Infrastructure 직접 의존

---

## 🔄 의존성 방향 (Dependency Rule)

### 핵심 원칙
**의존성은 항상 바깥쪽 → 안쪽으로만 흐른다.**

```
Presentation Layer
    ↓ depends on
Application Layer
    ↓ depends on
Domain Layer
    ↑ implemented by
Infrastructure Layer
```

### 중요 포인트

1. **Domain은 누구도 의존하지 않음**
   - Domain은 가장 안정적인 계층
   - Infrastructure를 모름 (인터페이스만 정의)

2. **Infrastructure는 Domain을 알지만, Domain은 Infrastructure를 모름**
   - Repository 인터페이스: Domain
   - Repository 구현체: Infrastructure

3. **상위 계층은 하위 계층을 의존할 수 있음**
   - Controller → UseCase ✅
   - UseCase → Repository Interface ✅
   - UseCase → DomainService ✅

4. **하위 계층은 상위 계층을 의존하면 안 됨**
   - Domain → UseCase ❌
   - Domain → Controller ❌

---

## ✅ Pass 기준

### 1. 아키텍처 분리
- [ ] 4계층(Presentation, Application, Domain, Infrastructure)이 명확히 분리
- [ ] 각 계층이 별도 패키지로 구성됨
- [ ] 의존성 방향이 올바름 (Domain이 Infrastructure를 의존하지 않음)

### 2. 책임 분리
- [ ] Controller는 HTTP 처리만 담당
- [ ] UseCase는 워크플로우 조율만 담당
- [ ] Entity는 비즈니스 로직 포함
- [ ] Repository 구현체는 Infrastructure에 위치

### 3. 코드 품질
- [ ] 순환 참조(Circular Dependency) 없음
- [ ] God Class 없음 (한 클래스에 모든 로직 집중)
- [ ] 각 클래스가 단일 책임 원칙(SRP) 준수

---

## ❌ Fail 사유

### Architecture Fail
- ❌ **계층 미분리**: 단일 파일에 Controller + Service + Repository 혼재
- ❌ **의존성 역전**: Domain이 Infrastructure를 직접 의존 (import)
- ❌ **책임 혼재**: Controller에 비즈니스 로직 작성

### Implementation Fail
- ❌ **비즈니스 로직 위치**: Controller나 Repository에 비즈니스 규칙 작성
- ❌ **God Service**: 하나의 Service에 모든 로직 집중
- ❌ **순환 참조**: A → B → A 의존 구조

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] 4계층의 역할을 설명할 수 있다
- [ ] 의존성 방향 규칙을 설명할 수 있다
- [ ] 각 계층의 책임을 구분할 수 있다

### 실전 적용
- [ ] 비즈니스 로직을 어느 계층에 둘지 판단할 수 있다
- [ ] Repository 인터페이스를 Domain에 둘 수 있다
- [ ] 순환 참조를 발견하고 해결할 수 있다

### 토론 주제
- "왜 Repository 인터페이스를 Domain에 두나요?"
- "Controller에서 직접 Repository를 호출하면 안 되는 이유는?"
- "UseCase에서 다른 UseCase를 호출하면 안 되는 이유는?"

---

## 📚 참고 자료

- [Martin Fowler - Presentation Domain Data Layering](https://martinfowler.com/bliki/PresentationDomainDataLayering.html)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [DDD - Eric Evans](https://www.domainlanguage.com/ddd/)

---

## 💡 실전 팁

### Controller 작성 시
```java
// ✅ 좋은 예
@GetMapping("/{id}")
public ApiResponse<ProductResponse> getProduct(@PathVariable String id) {
    return ApiResponse.success(productUseCase.getProduct(id));
}

// ❌ 나쁜 예 (비즈니스 로직 포함)
@GetMapping("/{id}")
public ApiResponse<ProductResponse> getProduct(@PathVariable String id) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    if (product.getStock() < 10) {  // 비즈니스 로직!
        // ...
    }

    return ApiResponse.success(ProductResponse.from(product));
}
```

### UseCase 작성 시
```java
// ✅ 좋은 예 (여러 도메인 조합)
public OrderResponse createOrder(CreateOrderRequest request) {
    Product product = productRepository.findById(request.getProductId())
        .orElseThrow(...);

    product.decreaseStock(request.getQuantity());  // Entity 메서드 호출

    Order order = Order.create(request);
    return OrderResponse.from(orderRepository.save(order));
}

// ❌ 나쁜 예 (비즈니스 로직 직접 작성)
public OrderResponse createOrder(CreateOrderRequest request) {
    Product product = productRepository.findById(request.getProductId())
        .orElseThrow(...);

    // 비즈니스 로직을 UseCase에 직접 작성 (Entity에 위임해야 함)
    if (product.getStock() < request.getQuantity()) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
    }
    product.setStock(product.getStock() - request.getQuantity());

    // ...
}
```

---

**다음 학습**: [02. 유스케이스 패턴](./02-usecase-pattern.md)
