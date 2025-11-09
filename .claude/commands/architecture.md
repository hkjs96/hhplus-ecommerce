---
description: 레이어드 아키텍처 상세 설명 및 프로젝트 구조
---

# Layered Architecture 상세 설계

## 🏗️ 의존성 방향 (Dependency Rule)

```
Presentation Layer (Controller)
    ↓ depends on
Application Layer (UseCase)
    ↓ depends on
Domain Layer (Entity, Repository Interface, DomainService)
    ↑ implemented by
Infrastructure Layer (In-Memory Repository Impl)
```

**핵심 원칙**: 의존성은 항상 **바깥쪽 → 안쪽**으로만 흐른다.
- Infrastructure는 Domain을 **알지만**, Domain은 Infrastructure를 **모른다**.
- Repository 인터페이스는 **Domain**에, 구현체는 **Infrastructure**에 위치.

---

## 📁 Project Structure (Step 5-6)

```
src/main/java/io/hhplus/ecommerce/
├── domain/                          # 🔵 Domain Layer
│   ├── product/
│   │   ├── Product.java            # Entity
│   │   ├── Stock.java              # Value Object
│   │   ├── ProductRepository.java  # Repository Interface
│   │   └── ProductService.java     # Domain Service (optional)
│   ├── order/
│   │   ├── Order.java              # Entity (Aggregate Root)
│   │   ├── OrderItem.java          # Entity
│   │   ├── OrderStatus.java        # Enum
│   │   ├── OrderRepository.java    # Repository Interface
│   │   └── OrderService.java       # Domain Service
│   ├── cart/
│   │   ├── Cart.java               # Entity (Aggregate Root)
│   │   ├── CartItem.java           # Entity
│   │   ├── CartRepository.java     # Repository Interface
│   │   └── CartService.java        # Domain Service
│   ├── coupon/
│   │   ├── Coupon.java             # Entity
│   │   ├── UserCoupon.java         # Entity
│   │   ├── CouponDiscount.java     # Value Object
│   │   ├── CouponRepository.java   # Repository Interface
│   │   ├── UserCouponRepository.java
│   │   └── CouponService.java      # Domain Service (선착순 로직)
│   └── user/
│       ├── User.java               # Entity
│       ├── Balance.java            # Value Object
│       ├── UserRepository.java     # Repository Interface
│       └── UserService.java        # Domain Service
│
├── application/                     # 🟢 Application Layer
│   ├── product/
│   │   ├── ProductUseCase.java     # 상품 조회 유스케이스
│   │   ├── PopularProductUseCase.java  # 인기 상품 조회
│   │   └── dto/
│   │       ├── ProductResponse.java
│   │       └── PopularProductResponse.java
│   ├── cart/
│   │   ├── CartUseCase.java        # 장바구니 관리
│   │   └── dto/
│   │       ├── AddCartItemRequest.java
│   │       └── CartResponse.java
│   ├── order/
│   │   ├── OrderUseCase.java       # 주문 생성
│   │   ├── PaymentUseCase.java     # 결제 처리
│   │   └── dto/
│   │       ├── CreateOrderRequest.java
│   │       ├── OrderResponse.java
│   │       └── PaymentResponse.java
│   ├── coupon/
│   │   ├── CouponUseCase.java      # 쿠폰 발급/조회
│   │   └── dto/
│   │       ├── IssueCouponRequest.java
│   │       └── IssueCouponResponse.java
│   └── user/
│       ├── UserUseCase.java        # 사용자 잔액 관리
│       └── dto/
│           ├── BalanceResponse.java
│           └── ChargeBalanceRequest.java
│
├── infrastructure/                  # 🟡 Infrastructure Layer
│   ├── persistence/
│   │   ├── product/
│   │   │   └── InMemoryProductRepository.java  # Repository 구현체
│   │   ├── order/
│   │   │   └── InMemoryOrderRepository.java
│   │   ├── cart/
│   │   │   ├── InMemoryCartRepository.java
│   │   │   └── InMemoryCartItemRepository.java
│   │   ├── coupon/
│   │   │   ├── InMemoryCouponRepository.java
│   │   │   └── InMemoryUserCouponRepository.java
│   │   └── user/
│   │       └── InMemoryUserRepository.java
│   └── config/
│       └── DataInitializer.java    # 초기 데이터 로딩
│
├── presentation/                    # 🔴 Presentation Layer
│   ├── api/
│   │   ├── product/
│   │   │   └── ProductController.java  # UseCase 호출
│   │   ├── cart/
│   │   │   └── CartController.java
│   │   ├── order/
│   │   │   └── OrderController.java
│   │   ├── coupon/
│   │   │   └── CouponController.java
│   │   └── user/
│   │       └── UserController.java
│   └── common/
│       ├── ApiResponse.java
│       ├── ErrorResponse.java
│       └── GlobalExceptionHandler.java
│
├── config/
│   ├── OpenApiConfig.java
│   └── AsyncConfig.java
│
└── common/
    └── exception/
        ├── BusinessException.java
        └── ErrorCode.java
```

---

## 📡 API Response Specification

### 주요 API 응답 형식

#### 1. 인기 상품 조회 (GET /products/top)

**Response:**
```json
{
  "success": true,
  "data": {
    "period": "3days",
    "products": [
      {
        "rank": 1,
        "productId": "P001",
        "name": "노트북",
        "salesCount": 150,
        "revenue": 133500000
      }
    ]
  }
}
```

**필수 필드:**
- `period`: "3days" (고정값)
- `rank`: 순위 (1~5)
- `salesCount`: 판매 수량
- `revenue`: 매출액

**집계 방식**:
- 최근 3일간 판매량 기준 Top 5
- 실시간 쿼리 (초기 구현)
- 향후 성능 이슈 시 배치/캐시로 개선

---

#### 2. 주문 생성 (POST /orders)

**Request:**
```json
{
  "userId": "user123",
  "items": [
    {
      "productId": "P001",
      "quantity": 2
    }
  ],
  "couponId": "COUPON_10"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "items": [
      {
        "productId": "P001",
        "name": "노트북",
        "quantity": 2,
        "unitPrice": 890000,
        "subtotal": 1780000
      }
    ],
    "subtotalAmount": 1900000,
    "discountAmount": 190000,
    "totalAmount": 1710000,
    "status": "PENDING"
  }
}
```

**필수 필드:**
- `items[]`: 주문 상품 상세 (name, unitPrice, subtotal 포함)
- `subtotalAmount`: 상품 합계 금액
- `discountAmount`: 할인 금액
- `totalAmount`: 최종 결제 금액
- `status`: "PENDING" | "COMPLETED"

---

#### 3. 결제 처리 (POST /orders/{orderId}/payment)

**Request:**
```json
{
  "userId": "user123"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "orderId": "ORDER-20240115-001",
    "paidAmount": 1710000,
    "remainingBalance": 290000,
    "status": "SUCCESS",
    "dataTransmission": "SUCCESS"
  }
}
```

**필수 필드:**
- `paidAmount`: 결제된 금액
- `remainingBalance`: 결제 후 남은 잔액
- `status`: "SUCCESS" | "FAILED"
- `dataTransmission`: "SUCCESS" | "FAILED" | "PENDING"

**중요**: 외부 전송 실패(`dataTransmission: "FAILED"`)여도 주문은 정상 완료 처리

---

#### 4. 쿠폰 발급 (POST /coupons/{couponId}/issue)

**Request:**
```json
{
  "userId": "user123"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "userCouponId": "UC-20240115-001",
    "couponName": "10% 할인쿠폰",
    "discountRate": 10,
    "expiresAt": "2024-12-31T23:59:59Z",
    "remainingQuantity": 95
  }
}
```

**필수 필드:**
- `userCouponId`: 발급된 쿠폰 ID (사용자별 고유)
- `remainingQuantity`: 남은 쿠폰 수량 (선착순 확인용)

---

#### 5. 보유 쿠폰 조회 (GET /users/{userId}/coupons)

**Response:**
```json
{
  "success": true,
  "data": {
    "coupons": [
      {
        "userCouponId": "UC-20240115-001",
        "couponName": "10% 할인쿠폰",
        "discountRate": 10,
        "status": "AVAILABLE",
        "expiresAt": "2024-12-31T23:59:59Z"
      }
    ]
  }
}
```

**status 타입:**
- `AVAILABLE`: 사용 가능
- `USED`: 사용됨
- `EXPIRED`: 만료됨

---

## 🚨 Error Codes Reference

### ErrorCode Enum 또는 Constants 클래스

```java
package io.hhplus.ecommerce.common.exception;

public class ErrorCode {

    // 상품 관련 (Product)
    public static final String PRODUCT_NOT_FOUND = "P001";      // 상품을 찾을 수 없음
    public static final String INSUFFICIENT_STOCK = "P002";     // 재고 부족

    // 주문 관련 (Order)
    public static final String INVALID_QUANTITY = "O001";       // 잘못된 수량 (0 이하)
    public static final String ORDER_NOT_FOUND = "O002";        // 주문을 찾을 수 없음
    public static final String INVALID_ORDER_STATUS = "O003";   // 주문 상태가 올바르지 않음

    // 결제 관련 (Payment)
    public static final String INSUFFICIENT_BALANCE = "PAY001"; // 잔액 부족
    public static final String PAYMENT_FAILED = "PAY002";       // 결제 처리 실패

    // 쿠폰 관련 (Coupon)
    public static final String COUPON_SOLD_OUT = "C001";        // 쿠폰 수량 소진
    public static final String INVALID_COUPON = "C002";         // 유효하지 않은 쿠폰
    public static final String EXPIRED_COUPON = "C003";         // 만료된 쿠폰
    public static final String ALREADY_ISSUED = "C004";         // 이미 발급받은 쿠폰 (1인 1매)

    // 사용자 관련 (User)
    public static final String USER_NOT_FOUND = "U001";         // 사용자를 찾을 수 없음
    public static final String INVALID_CHARGE_AMOUNT = "U002";  // 잘못된 충전 금액
}
```

### BusinessException 클래스 예시

```java
package io.hhplus.ecommerce.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final String message;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

    // 편의 메서드
    public static BusinessException of(String errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}
```

### 사용 예시

```java
// Domain Layer에서 사용
public void decreaseStock(int quantity) {
    if (stock < quantity) {
        throw new BusinessException(
            ErrorCode.INSUFFICIENT_STOCK,
            String.format("재고가 부족합니다. (요청: %d, 재고: %d)", quantity, stock)
        );
    }
    this.stock -= quantity;
}

// UseCase에서 사용
public ProductResponse getProduct(String productId) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.PRODUCT_NOT_FOUND,
            "상품을 찾을 수 없습니다. productId: " + productId
        ));

    return ProductResponse.from(product);
}
```

---

## 🗂️ Data Initialization Strategy

### DataInitializer 구현

```java
package io.hhplus.ecommerce.infrastructure.config;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        initProducts();
        initUsers();
    }

    private void initProducts() {
        productRepository.save(new Product("P001", "노트북", "고성능 게이밍 노트북", 890000L, 10, "전자제품"));
        productRepository.save(new Product("P002", "키보드", "기계식 키보드", 120000L, 20, "주변기기"));
        productRepository.save(new Product("P003", "마우스", "무선 마우스", 45000L, 30, "주변기기"));
        productRepository.save(new Product("P004", "모니터", "27인치 4K 모니터", 350000L, 15, "전자제품"));
        productRepository.save(new Product("P005", "헤드셋", "노이즈 캔슬링 헤드셋", 230000L, 25, "주변기기"));
    }

    private void initUsers() {
        userRepository.save(new User("U001", "김항해", 50000));
        userRepository.save(new User("U002", "이플러스", 100000));
        userRepository.save(new User("U003", "박백엔드", 30000));
    }
}
```

---

## 🔧 Best Practices (Coach Feedback)

### 1. Repository Pattern - `findByIdOrThrow()` 커스텀 메서드

#### 문제점: 반복되는 코드 패턴

**기존 방식 (반복적):**
```java
// CouponService
Coupon coupon = couponRepository.findById(couponId)
    .orElseThrow(() -> new BusinessException(
        ErrorCode.INVALID_COUPON,
        "쿠폰을 찾을 수 없습니다. couponId: " + couponId
    ));

// CartService
Cart cart = cartRepository.findById(cartId)
    .orElseThrow(() -> new BusinessException(
        ErrorCode.CART_NOT_FOUND,
        "장바구니를 찾을 수 없습니다. cartId: " + cartId
    ));

// UserService
User user = userRepository.findById(userId)
    .orElseThrow(() -> new BusinessException(
        ErrorCode.USER_NOT_FOUND,
        "사용자를 찾을 수 없습니다. userId: " + userId
    ));
```

**문제점:**
- ❌ 동일한 패턴이 모든 Service에 반복됨
- ❌ 코드 중복 (100+ 라인)
- ❌ 에러 메시지 일관성 유지 어려움
- ❌ 실수로 다른 ErrorCode 사용 가능

---

#### 해결책: Repository에 Default Method 추가 (권장)

**1단계: Repository 인터페이스에 추가**

```java
// Domain Repository Interface
public interface ProductRepository {

    Optional<Product> findById(String id);

    Product save(Product product);

    /**
     * ID로 Product를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id Product ID
     * @return Product 엔티티
     * @throws BusinessException 상품을 찾을 수 없을 때
     */
    default Product findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다. productId: " + id
            ));
    }
}
```

**2단계: Service에서 사용**

```java
// ✅ After improvement (간결!)
public ProductResponse getProduct(String productId) {
    Product product = productRepository.findByIdOrThrow(productId);
    return ProductResponse.from(product);
}
```

---

#### 장점 비교

| 항목 | 기존 방식 | findByIdOrThrow() |
|------|----------|-------------------|
| **코드 라인** | 5줄 | 1줄 |
| **중복 코드** | 많음 (100+ 라인) | 없음 |
| **일관성** | 수동 관리 (실수 가능) | 자동 보장 |
| **타입 안전성** | 보통 | 높음 (각 Repository별 ErrorCode) |
| **IDE 지원** | 보통 | 우수 (자동완성) |
| **유지보수** | 어려움 | 쉬움 (한 곳만 수정) |

---

#### 적용 대상

모든 `findById()`를 가진 Repository에 적용:
- ✅ ProductRepository
- ✅ UserRepository
- ✅ CouponRepository
- ✅ OrderRepository
- ✅ CartItemRepository

---

#### 대안: BaseRepository (고급)

더 일반화된 접근:

```java
// Base Repository Interface
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    default T findByIdOrThrow(ID id, ErrorCode errorCode, String message) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(errorCode, message));
    }
}

// 각 Repository는 BaseRepository 상속
public interface CouponRepository extends BaseRepository<Coupon, Long> {
    // 추가 메서드만 정의
}
```

**트레이드오프:**
- ✅ 더 일반화됨
- ❌ ErrorCode를 호출 시마다 전달해야 함 (덜 간결)
- ❌ 각 Repository별 특화된 에러 메시지 불가

**결론: Option 1 (각 Repository별 default 메서드) 권장**

---

### 2. Validation Layer 분리 전략

#### 원칙: 계층별 검증 책임

```
Input Validation Flow:
Controller (형식) → UseCase (비즈니스) → Entity (도메인 규칙)

1️⃣ Controller: @Valid, @NotNull, @Min, @Max (형식 검증)
2️⃣ UseCase: 존재 여부, 권한, 상태 검증 (비즈니스 검증)
3️⃣ Entity: 도메인 규칙 (재고 부족, 수량 제한 등)
```

---

#### 계층별 구현 예시

**1️⃣ Controller Layer - 형식 검증**

```java
@PostMapping("/orders")
public ApiResponse<OrderResponse> createOrder(
    @Valid @RequestBody CreateOrderRequest request  // @Valid 적용
) {
    return ApiResponse.success(orderUseCase.createOrder(request));
}
```

**Request DTO:**
```java
public class CreateOrderRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다")
    @Size(min = 1, max = 10, message = "최대 10개까지 주문 가능합니다")
    private List<OrderItemRequest> items;

    @Positive(message = "쿠폰 ID는 양수여야 합니다")
    private Long couponId;
}
```

**검증 항목:**
- ✅ Null 체크
- ✅ 형식 검증 (이메일, 전화번호 등)
- ✅ 범위 검증 (최소/최대값)
- ✅ 길이 검증 (문자열, 리스트)

---

**2️⃣ UseCase Layer - 비즈니스 검증**

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 사용자 존재 확인
        User user = userRepository.findByIdOrThrow(request.getUserId());

        // 2. 쿠폰 유효성 검증 (선택적)
        if (request.getCouponId() != null) {
            validateCoupon(request.getUserId(), request.getCouponId());
        }

        // 3. 상품 존재 및 재고 확인
        for (OrderItemRequest item : request.getItems()) {
            Product product = productRepository.findByIdOrThrow(item.getProductId());
            // Entity의 도메인 규칙 호출
            product.validateStock(item.getQuantity());
        }

        // ...
    }

    private void validateCoupon(String userId, Long couponId) {
        // 쿠폰 소유 여부, 사용 가능 여부 등
        if (!userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(
                ErrorCode.INVALID_COUPON,
                "보유하지 않은 쿠폰입니다."
            );
        }
    }
}
```

**검증 항목:**
- ✅ 리소스 존재 확인
- ✅ 권한 검증
- ✅ 상태 검증 (주문 가능 상태, 쿠폰 사용 가능 등)

---

**3️⃣ Entity Layer - 도메인 규칙 검증**

```java
@Getter
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private Long price;
    private Integer stock;

    /**
     * 재고 검증 (도메인 규칙)
     */
    public void validateStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_QUANTITY,
                "수량은 1 이상이어야 합니다."
            );
        }

        if (stock < quantity) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. (요청: %d, 재고: %d)", quantity, stock)
            );
        }
    }

    /**
     * 재고 차감 (도메인 로직)
     */
    public void decreaseStock(int quantity) {
        validateStock(quantity);  // 먼저 검증
        this.stock -= quantity;    // 도메인 규칙 적용
    }

    /**
     * 재고 복구
     */
    public void restoreStock(int quantity) {
        this.stock += quantity;
    }
}
```

**검증 항목:**
- ✅ 비즈니스 규칙 (재고 부족, 수량 제한)
- ✅ 도메인 불변식 (Invariant)
- ✅ 상태 전이 규칙

---

#### 계층별 책임 비교표

| 계층 | 검증 대상 | 검증 방법 | 예시 |
|------|----------|----------|------|
| **Controller** | 입력 형식 | `@Valid`, `@NotNull`, `@Min` | "userId는 필수", "수량은 1 이상" |
| **UseCase** | 비즈니스 조건 | `findByIdOrThrow()`, 상태 체크 | "사용자 존재 확인", "쿠폰 소유 확인" |
| **Entity** | 도메인 규칙 | `throw BusinessException` | "재고 부족", "수량 0 이하" |

---

#### 안티패턴 (피해야 할 것)

❌ **Controller에 비즈니스 로직:**
```java
// ❌ Bad
@PostMapping("/products/{id}/purchase")
public ApiResponse purchase(@PathVariable String id, @RequestParam int quantity) {
    Product product = productRepository.findById(id).orElseThrow();

    // Controller에 비즈니스 로직 (안 됨!)
    if (product.getStock() < quantity) {
        throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
    }

    product.setStock(product.getStock() - quantity);
    productRepository.save(product);

    return ApiResponse.success();
}
```

✅ **올바른 분리:**
```java
// ✅ Good - Controller는 위임만
@PostMapping("/products/{id}/purchase")
public ApiResponse purchase(@PathVariable String id, @RequestParam int quantity) {
    return ApiResponse.success(productService.purchase(id, quantity));
}

// Service는 조율
@Service
public class ProductService {
    public void purchase(String id, int quantity) {
        Product product = productRepository.findByIdOrThrow(id);
        product.decreaseStock(quantity);  // Entity의 도메인 로직 호출
        productRepository.save(product);
    }
}

// Entity는 비즈니스 규칙
public class Product {
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

---

#### 핵심 원칙

1. **Single Responsibility Principle (SRP)**
   - Controller: HTTP 요청/응답 처리
   - UseCase: 비즈니스 흐름 조율
   - Entity: 도메인 규칙 캡슐화

2. **Don't Repeat Yourself (DRY)**
   - 검증 로직은 한 곳에만 (Entity)
   - 여러 곳에서 재사용

3. **Fail Fast**
   - Controller에서 먼저 형식 검증
   - 빠른 피드백으로 불필요한 처리 방지

---

## 📚 관련 명령어

- `/week3-guide` - Week 3 전체 가이드
- `/concurrency` - 동시성 제어 패턴
- `/testing` - 테스트 전략
- `/implementation` - 구현 가이드 및 코드 예시
- `/week3-faq` - Week 3 FAQ
