# 2. 유스케이스 패턴 (UseCase Pattern)

## 📌 핵심 개념

**UseCase는 사용자가 특정 목표를 달성하기 위해 시스템과 상호작용하는 완전한 시나리오입니다.**

---

## 🏗️ UseCase in Layered Architecture (중요)

### 핵심 정리

**4-레이어드 아키텍처에서 "Application Service"와 "UseCase"는 개념적으로 동일한 역할입니다.**

```
핵사고날/클린 아키텍처          레이어드 아키텍처
┌─────────────────────┐       ┌─────────────────────┐
│  Use Case           │       │  Application Layer  │
│  (Port)             │  ≈    │  (Application       │
│                     │       │   Service)          │
└─────────────────────┘       └─────────────────────┘
```

### 이름만 다를 뿐, 역할은 같다

**Application Service = UseCase**
- **같은 개념**: 둘 다 "여러 도메인 객체를 조율해 사용자 요구사항(유스케이스)를 완성하는 계층"
- **이름 차이**:
  - 핵사고날/클린 아키텍처: "UseCase" 또는 "Port" 용어 선호
  - 레이어드 아키텍처: "Application Service" 용어 선호
- **Week 3 권장**: 레이어드 아키텍처이므로 "Service" 네이밍 사용 (예: `ProductService`, `OrderService`)

### UseCase를 별도 계층으로 만들 필요는 없다

**잘못된 이해:**
```
❌ 잘못된 구조
Application Layer
  ├── UseCase (별도 계층?)
  └── Service (또 다른 계층?)
```

**올바른 이해:**
```
✅ 올바른 구조
Application Layer (= UseCase 역할을 하는 계층)
  ├── ProductService.java
  ├── OrderService.java
  └── CouponService.java
```

---

## 🤔 그럼 언제 UseCase 클래스를 분리하나?

### 결론: Application 계층 내부의 구조화 전략

**UseCase 클래스 분리는 선택사항입니다:**
- **한 도메인에 여러 유스케이스**가 있을 때, 가독성을 위해 분리할 수 있음
- **필수가 아님** - 단일 Service 클래스로 구현해도 무방

### 의사결정 체크리스트

| 기준 | 단일 Service | UseCase 분리 |
|------|-------------|-------------|
| **유스케이스 개수** | 1~3개 | 4개 이상 |
| **파일 크기** | 200줄 이하 | 200줄 초과 |
| **트랜잭션 복잡도** | 단순 | 복잡 (여러 도메인 조율) |
| **팀 컨벤션** | Service 선호 | UseCase 선호 |
| **Week 3 권장** | ✅ 단일 Service | △ 필요시 분리 |

### 패턴 1: 단일 ApplicationService (권장)

**언제 사용?**
- Week 3처럼 도메인당 유스케이스가 적을 때 (3~5개)
- 파일이 200줄 이하로 관리 가능할 때
- 팀이 전통적인 레이어드 아키텍처에 익숙할 때

```java
package io.hhplus.ecommerce.application.product;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    // UseCase 1: 상품 목록 조회
    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
            .map(ProductResponse::from)
            .toList();
    }

    // UseCase 2: 상품 상세 조회
    public ProductDetailResponse getProductDetail(String productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Integer stock = stockRepository.getAvailableStock(productId);
        return ProductDetailResponse.of(product, stock);
    }

    // UseCase 3: 인기 상품 조회
    public List<ProductResponse> getTopProducts() {
        return productRepository.findTopProducts(3, 5);
    }
}
```

**장점:**
- ✅ 간결한 구조 (파일 1개로 모든 유스케이스 관리)
- ✅ 레이어드 아키텍처 전통 방식
- ✅ Week 3 수준에 적합

**단점:**
- ❌ 유스케이스가 많아지면 파일이 비대해질 수 있음

### 패턴 2: UseCase 클래스로 분리

**언제 사용?**
- 한 도메인에 유스케이스가 많을 때 (5개 이상)
- 각 유스케이스가 복잡한 트랜잭션을 포함할 때
- Clean Architecture 스타일을 선호할 때

```java
package io.hhplus.ecommerce.application.order;

// UseCase 1: 주문 생성
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;

    public OrderResponse execute(CreateOrderRequest request) {
        // 복잡한 주문 생성 로직 (30~50줄)
        // ...
    }
}

// UseCase 2: 주문 취소
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    public void execute(String orderId) {
        // 복잡한 주문 취소 로직 (30~50줄)
        // ...
    }
}

// UseCase 3: 주문 상태 조회
@Service
@RequiredArgsConstructor
public class GetOrderStatusUseCase {
    private final OrderRepository orderRepository;

    public OrderStatusResponse execute(String orderId) {
        // 주문 상태 조회 로직
        // ...
    }
}
```

**장점:**
- ✅ 각 유스케이스가 명확히 분리됨 (단일 책임 원칙)
- ✅ 복잡한 트랜잭션 로직을 독립적으로 관리
- ✅ 테스트 격리가 쉬움

**단점:**
- ❌ 파일 개수 증가 (유스케이스당 1개 파일)
- ❌ Week 3 수준에는 과도할 수 있음

---

## 🎯 Week 3 실전 가이드

### 권장 구조

```
src/main/java/io/hhplus/ecommerce/
└── application/
    ├── product/
    │   ├── ProductService.java          # 모든 상품 유스케이스
    │   └── dto/
    │       ├── ProductResponse.java
    │       └── ProductDetailResponse.java
    ├── order/
    │   ├── OrderService.java             # 모든 주문 유스케이스
    │   ├── PaymentService.java           # 결제 관련 유스케이스
    │   └── dto/
    │       ├── CreateOrderRequest.java
    │       └── OrderResponse.java
    └── coupon/
        ├── CouponService.java            # 모든 쿠폰 유스케이스
        └── dto/
            ├── IssueCouponRequest.java
            └── CouponResponse.java
```

### Application Layer 작성 규칙

#### 1. 트랜잭션 단위
```java
@Service
@RequiredArgsConstructor
public class OrderService {

    @Transactional  // UseCase = 트랜잭션 단위
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1개의 UseCase = 1개의 트랜잭션
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        // 조회 UseCase = readOnly 트랜잭션
    }
}
```

#### 2. DTO 변환 책임
```java
@Service
@RequiredArgsConstructor
public class ProductService {

    public ProductResponse getProduct(String productId) {
        // Domain → DTO 변환은 Application Layer 책임
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return ProductResponse.from(product);  // DTO 변환
    }
}
```

#### 3. 여러 도메인 조율
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;

    public OrderResponse createOrder(CreateOrderRequest request) {
        // Application Service는 여러 Repository를 조율
        User user = userRepository.findById(request.getUserId())...
        List<Product> products = productRepository.findByIds(...)...
        Coupon coupon = couponRepository.findById(...)...

        // 도메인 객체들을 조합하여 비즈니스 로직 수행
        // ...
    }
}
```

---

## ❌ 안티패턴 (Anti-patterns)

### 1. UseCase를 별도 계층으로 오해
```java
❌ 잘못된 구조
Application Layer
  ├── usecase/
  │   └── CreateOrderUseCase.java
  └── service/
      └── OrderApplicationService.java  # 중복!
```

**문제점:** UseCase와 Service를 별도 계층으로 만들어 중복 발생

### 2. UseCase가 다른 UseCase를 직접 호출
```java
❌ 잘못된 코드
@Service
public class OrderService {
    @Autowired
    private PaymentService paymentService;  # 다른 Application Service 주입

    public OrderResponse createOrder(...) {
        // ...
        paymentService.processPayment(...);  # UseCase → UseCase 호출
    }
}
```

**올바른 방법:**
- **Domain Service로 분리** 또는 **하나의 UseCase로 통합**

### 3. Application Service에 도메인 로직 작성
```java
❌ 잘못된 코드
@Service
public class ProductService {
    public void decreaseStock(String productId, int quantity) {
        Product product = productRepository.findById(productId)...

        // 도메인 로직을 Application Layer에 작성 (잘못됨)
        if (product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        product.setStock(product.getStock() - quantity);
    }
}
```

**올바른 방법:**
```java
✅ 올바른 코드
@Service
public class ProductService {
    public void decreaseStock(String productId, int quantity) {
        Product product = productRepository.findById(productId)...

        // 도메인 로직은 Entity 메서드로 위임
        product.decreaseStock(quantity);  # Entity가 비즈니스 규칙 처리

        productRepository.save(product);
    }
}
```

---

## 🎯 UseCase의 정의

### 로이코치님 정의
> "유즈케이스는 요구사항의 단위이며, 아키텍처 패턴과 무관합니다."

### 특징
- 📋 **요구사항의 단위**: 하나의 비즈니스 목표를 달성하는 완전한 흐름
- 🔄 **여러 도메인 조합**: 단순 CRUD가 아닌 복합적인 비즈니스 시나리오
- 🎯 **사용자 관점**: 사용자가 달성하고자 하는 목표 중심

---

## 💡 UseCase vs 단순 CRUD

### ❌ 단순 CRUD (나쁜 예)
```java
@Service
public class ProductService {
    // 단순히 DB에서 조회만
    public Product getProduct(String productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
```

### ✅ UseCase (좋은 예)
```java
@Service
@RequiredArgsConstructor
public class ProductDetailUseCase {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final StockRepository stockRepository;
    private final ShippingRepository shippingRepository;

    /**
     * 고객이 구매 결정을 내리는데 필요한 모든 정보를 제공
     *
     * 사용자 목표: 상품 상세 정보를 보고 구매 여부를 결정한다
     *
     * 제공 정보:
     * - 상품 기본 정보 (이름, 가격, 설명)
     * - 실시간 재고 수량
     * - 평균 평점 및 리뷰 개수
     * - 배송 예정일
     * - 함께 구매하면 좋은 상품 추천
     */
    public ProductDetailResponse getProductDetail(String productId) {
        // 1. 상품 기본 정보 조회
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 2. 재고 정보 조회
        Integer stockQuantity = stockRepository.getAvailableStock(productId);

        // 3. 평점/리뷰 통계 조회
        ReviewStats stats = reviewRepository.getStatsByProduct(productId);

        // 4. 배송 예정일 계산
        LocalDate estimatedDelivery = shippingRepository.calculateDeliveryDate(productId);

        // 5. 추천 상품 조회
        List<Product> recommendations = productRepository.findRecommendations(productId);

        // 6. 응답 DTO 구성
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

**차이점:**
- 단순 CRUD: 단일 데이터 조회
- UseCase: **여러 도메인을 조합**하여 사용자의 목표를 달성

---

## 📋 UseCase 작성 원칙

### 1. API 명세 = UseCase
**1 API Endpoint = 1 UseCase 메서드**

```java
// API 명세
POST /orders

// UseCase 구현
public class OrderUseCase {
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 주문 생성 유스케이스 구현
    }
}
```

### 2. 완전한 비즈니스 플로우
UseCase는 시작부터 끝까지 완전한 흐름을 포함

```java
public OrderResponse createOrder(CreateOrderRequest request) {
    // 1. 입력 검증
    validateRequest(request);

    // 2. 상품 조회
    List<Product> products = getProducts(request.getItems());

    // 3. 재고 검증
    validateStock(products, request.getItems());

    // 4. 쿠폰 검증 및 할인 계산
    Coupon coupon = applyCoupon(request.getCouponId());
    long discountAmount = calculateDiscount(products, coupon);

    // 5. 주문 생성
    Order order = createOrder(request, discountAmount);

    // 6. 재고 차감
    decreaseStock(products, request.getItems());

    // 7. 응답 반환
    return OrderResponse.from(order);
}
```

### 3. 여러 도메인 조율
UseCase는 여러 DomainService와 Repository를 조율

```java
@RequiredArgsConstructor
public class OrderUseCase {
    // 여러 Repository 의존
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;

    // DomainService 의존
    private final OrderService orderService;
    private final PaymentService paymentService;
}
```

---

## 🔄 UseCase vs DomainService

### 비교표

| 항목 | UseCase | DomainService |
|------|---------|---------------|
| **위치** | Application Layer | Domain Layer |
| **역할** | 워크플로우 조율 | 도메인 로직 |
| **의존성** | Repository, DomainService | Entity, Value Object |
| **예시** | `createOrder()` | `validateOrder()` |
| **테스트** | Mock 필요 | Mock 불필요 (순수 로직) |

### 실전 예시

```java
// DomainService (Domain Layer)
@Service
public class OrderService {
    /**
     * 여러 Entity를 조합한 도메인 로직
     * 외부 의존성 없음 (순수 비즈니스 로직)
     */
    public void validateOrder(Order order, List<Product> products) {
        // 주문 유효성 검증
        if (order.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_ORDER);
        }

        // 재고 검증
        for (OrderItem item : order.getItems()) {
            Product product = findProduct(products, item.getProductId());
            if (!product.hasStock(item.getQuantity())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }

    /**
     * 총 주문 금액 계산
     */
    public long calculateTotalAmount(List<Product> products, List<OrderItem> items) {
        return items.stream()
            .mapToLong(item -> {
                Product product = findProduct(products, item.getProductId());
                return product.getPrice() * item.getQuantity();
            })
            .sum();
    }
}

// UseCase (Application Layer)
@Service
@RequiredArgsConstructor
public class OrderUseCase {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;  // DomainService 사용

    /**
     * 주문 생성 워크플로우 조율
     * 여러 도메인을 조합하여 완전한 비즈니스 플로우 구성
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 데이터 조회 (Repository)
        List<Product> products = productRepository.findByIds(
            request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .toList()
        );

        // 2. 주문 생성
        Order order = Order.create(request.getUserId(), request.getItems());

        // 3. 비즈니스 규칙 검증 (DomainService)
        orderService.validateOrder(order, products);

        // 4. 총 금액 계산 (DomainService)
        long totalAmount = orderService.calculateTotalAmount(products, order.getItems());
        order.setTotalAmount(totalAmount);

        // 5. 재고 차감 (Entity)
        products.forEach(product ->
            product.decreaseStock(getQuantity(order.getItems(), product.getId()))
        );

        // 6. 주문 저장 (Repository)
        Order savedOrder = orderRepository.save(order);

        // 7. DTO 변환
        return OrderResponse.from(savedOrder);
    }
}
```

**역할 분리:**
- **UseCase**: 흐름 조율 (조회 → 검증 → 계산 → 저장)
- **DomainService**: 순수 비즈니스 로직 (검증, 계산)
- **Entity**: 자신의 상태 변경 (재고 차감)

---

## 🎨 UseCase 네이밍 규칙

### 추천 패턴
```
{비즈니스_동작}UseCase

예시:
- OrderUseCase
- ProductUseCase
- CouponUseCase
- PaymentUseCase
```

### 메서드 네이밍
```java
// ✅ 좋은 예 (비즈니스 용어 사용)
createOrder(CreateOrderRequest)
processPayment(PaymentRequest)
issueCoupon(IssueCouponRequest)

// ❌ 나쁜 예 (기술 용어 사용)
insertOrder(OrderDto)
executePayment(PaymentDto)
saveCoupon(CouponDto)
```

---

## ✅ Pass 기준

### UseCase 구현
- [ ] API 명세가 UseCase 메서드로 구현됨 (1 API = 1 UseCase 메서드)
- [ ] 각 UseCase는 완전한 비즈니스 플로우를 포함
- [ ] 여러 도메인을 조합하여 사용자 목표를 달성

### 코드 품질
- [ ] UseCase는 Application Layer에 위치
- [ ] DomainService와 역할이 명확히 분리됨
- [ ] 단일 책임 원칙(SRP) 준수

### 네이밍
- [ ] 비즈니스 용어 사용 (유비쿼터스 언어)
- [ ] 의도가 명확한 메서드명

---

## ❌ Fail 사유

### UseCase Fail
- ❌ **단순 CRUD**: 단일 데이터 조회/저장만 수행
- ❌ **불완전한 플로우**: 사용자 목표를 달성하지 못함
- ❌ **UseCase 직접 호출**: UseCase가 다른 UseCase를 직접 호출

### 구현 Fail
- ❌ **비즈니스 로직 포함**: UseCase에 도메인 규칙 직접 작성
- ❌ **God UseCase**: 하나의 UseCase에 모든 로직 집중
- ❌ **기술 용어 사용**: insert, select, update 등 기술 용어 사용

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] UseCase의 정의를 설명할 수 있다
- [ ] UseCase와 DomainService의 차이를 설명할 수 있다
- [ ] 단순 CRUD와 UseCase의 차이를 설명할 수 있다

### 실전 적용
- [ ] API 명세를 보고 UseCase 메서드를 정의할 수 있다
- [ ] 여러 도메인을 조합한 UseCase를 작성할 수 있다
- [ ] 비즈니스 로직을 DomainService로 분리할 수 있다

### 토론 주제
- "상품 조회 API를 UseCase로 구현한다면 어떤 정보를 포함해야 할까요?"
- "UseCase에서 다른 UseCase를 호출하면 안 되는 이유는 무엇인가요?"
- "DomainService 없이 UseCase만으로 구현하면 어떤 문제가 생기나요?"

---

## 📚 참고 자료

- [Use Cases - Martin Fowler](https://martinfowler.com/bliki/UseCases.html)
- [Application Service vs Domain Service](https://enterprisecraftsmanship.com/posts/domain-vs-application-services/)
- CLAUDE.md - Q3. UseCase란 무엇인가요?

---

## 💡 실전 팁

### UseCase 작성 순서
1. **사용자 목표 파악**: "사용자가 무엇을 하려고 하는가?"
2. **필요한 정보 나열**: 목표 달성에 필요한 모든 데이터
3. **플로우 설계**: 시작부터 끝까지의 흐름
4. **도메인 조합**: 여러 Repository, DomainService 조율
5. **DTO 변환**: 결과를 Response DTO로 변환

### Good Example
```java
/**
 * UseCase: 선착순 쿠폰 발급
 *
 * 사용자 목표: 한정된 수량의 쿠폰을 선착순으로 발급받는다
 *
 * 비즈니스 규칙:
 * - 1인 1매 제한
 * - 수량 소진 시 실패
 * - 만료된 쿠폰은 발급 불가
 */
@Service
@RequiredArgsConstructor
public class CouponUseCase {
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    public IssueCouponResponse issueCoupon(String userId, String couponId) {
        // 1. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COUPON));

        // 2. 쿠폰 유효성 검증 (Entity 메서드)
        coupon.validateIssuable();

        // 3. 중복 발급 체크
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(ErrorCode.ALREADY_ISSUED);
        }

        // 4. 쿠폰 발급 (Entity 메서드 - 동시성 제어 포함)
        boolean issued = coupon.tryIssue();
        if (!issued) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 5. 사용자 쿠폰 생성
        UserCoupon userCoupon = UserCoupon.create(userId, couponId);
        userCouponRepository.save(userCoupon);

        // 6. 응답 반환
        return IssueCouponResponse.of(userCoupon, coupon.getRemainingQuantity());
    }
}
```

---

**이전 학습**: [01. 레이어드 아키텍처](./01-layered-architecture.md)
**다음 학습**: [03. 도메인 모델링](./03-domain-modeling.md)
