# 2. 유스케이스 패턴 (UseCase Pattern)

## 📌 핵심 개념

**UseCase는 사용자가 특정 목표를 달성하기 위해 시스템과 상호작용하는 완전한 시나리오입니다.**

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
