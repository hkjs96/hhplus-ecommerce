# 7. DTO 설계 전략 (DTO Design Strategy)

## 📌 핵심 개념

**DTO (Data Transfer Object)**: 계층 간 데이터 전송을 위한 객체

---

## 🎯 레이어별 DTO 분리 원칙

### 로이코치님 조언
> "레이어별로 관심사와 변경 이유가 다르기 때문에 레이어는 자신만의 DTO를 가져야 합니다."

### 소프트웨어 핵심 원칙
**"변경 이유가 다른 것은 분리한다"**

---

## 📋 원칙 vs 실용

### 원칙 (이상적)
```
각 레이어별 DTO 분리

Presentation: Request/Response DTO
Application: Command/Query DTO
Domain: Entity (DTO 사용 안 함)
Infrastructure: Data Entity (JPA Entity 등)
```

### 실용 (Week 3)
```
도메인 모델이 안정적이면 여러 레이어에서 사용 가능

✅ Domain Entity를 Application/Presentation에서 사용 OK
⚠️ 실무에서는 레이어별 DTO 분리 권장
```

---

## 🔄 DTO 재사용 전략

### Composition 활용

```java
// 공통 필드를 Base DTO로 분리
@Getter
@AllArgsConstructor
public class ProductBaseDto {
    private String productId;
    private String name;
    private Long price;
}

// API별 전용 DTO (Composition)
@Getter
@AllArgsConstructor
public class ProductListResponse {
    private ProductBaseDto product;  // 컴포지션
    private Integer stock;
    private boolean available;

    public static ProductListResponse from(Product product) {
        ProductBaseDto base = new ProductBaseDto(
            product.getId(),
            product.getName(),
            product.getPrice()
        );
        return new ProductListResponse(
            base,
            product.getStock(),
            product.getStock() > 0
        );
    }
}

// 상세 정보는 다른 필드 추가
@Getter
@AllArgsConstructor
public class ProductDetailResponse {
    private ProductBaseDto product;  // 같은 Base 재사용
    private Integer stock;
    private String description;
    private List<ReviewDto> reviews;
    private Double avgRating;

    public static ProductDetailResponse from(Product product, List<Review> reviews) {
        ProductBaseDto base = new ProductBaseDto(
            product.getId(),
            product.getName(),
            product.getPrice()
        );
        return new ProductDetailResponse(
            base,
            product.getStock(),
            product.getDescription(),
            reviews.stream().map(ReviewDto::from).toList(),
            calculateAvgRating(reviews)
        );
    }
}
```

**장점:**
- ✅ 공통 부분 재사용 (DRY 원칙)
- ✅ API별 독립성 유지 (SRP 원칙)
- ✅ 변경 영향 최소화

---

## 🏗️ API별 전용 DTO vs 공통 DTO

### 단일 책임 원칙 (SRP)
```java
// ✅ 좋은 예: API별 전용 DTO
public class CreateOrderRequest {
    private String userId;
    private List<OrderItemRequest> items;
    private String couponId;  // 주문 생성시만 필요
}

public class OrderListResponse {
    private String orderId;
    private OrderStatus status;
    private Long totalAmount;
    // 목록 조회시 필요한 필드만
}

public class OrderDetailResponse {
    private String orderId;
    private OrderStatus status;
    private Long totalAmount;
    private List<OrderItemResponse> items;  // 상세 조회시 추가
    private String shippingAddress;
    private LocalDateTime createdAt;
}

// ❌ 나쁜 예: 모든 API에 공통 DTO
public class OrderDto {
    private String orderId;
    private String userId;
    private List<OrderItemDto> items;
    private String couponId;
    private String shippingAddress;
    // 모든 필드 포함 → 어떤 API에서 뭘 쓰는지 불명확
}
```

### DRY 원칙
```java
// ✅ 좋은 예: 공통 부분 Composition
public class OrderBaseDto {
    private String orderId;
    private Long totalAmount;
    private OrderStatus status;
}

public class OrderListResponse {
    private OrderBaseDto order;  // 공통 부분 재사용
}

public class OrderDetailResponse {
    private OrderBaseDto order;  // 공통 부분 재사용
    private List<OrderItemResponse> items;  // 추가 필드
}
```

---

## 📍 입력값 검증 레이어

### 로이코치님 조언
> "입력값 검증 흐름: Controller (형식 검증) → Entity (비즈니스 규칙 검증)"

### Controller: 형식 검증
```java
@PostMapping("/orders")
public ApiResponse<OrderResponse> createOrder(
    @Valid @RequestBody CreateOrderRequest request  // @Valid로 형식 검증
) {
    return ApiResponse.success(orderUseCase.createOrder(request));
}

// Request DTO: 형식 검증 어노테이션
@Getter
@AllArgsConstructor
public class CreateOrderRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다")
    private List<OrderItemRequest> items;

    @Size(max = 20, message = "쿠폰 ID는 20자 이하여야 합니다")
    private String couponId;
}

@Getter
@AllArgsConstructor
public class OrderItemRequest {
    @NotBlank
    private String productId;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    @Max(value = 100, message = "수량은 100 이하여야 합니다")
    private Integer quantity;
}
```

### Entity: 비즈니스 규칙 검증
```java
public class Product {
    public void decreaseStock(int quantity) {
        // 비즈니스 규칙 검증
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }
}
```

---

## 🎨 DTO 변환 패턴

### Static Factory Method (권장)
```java
@Getter
@AllArgsConstructor
public class ProductResponse {
    private String productId;
    private String name;
    private Long price;
    private Integer stock;

    /**
     * Entity → Response DTO 변환
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getStock()
        );
    }
}

// 사용
ProductResponse response = ProductResponse.from(product);
```

### Builder 패턴 (복잡한 경우)
```java
@Getter
@Builder
public class OrderResponse {
    private String orderId;
    private List<OrderItemResponse> items;
    private Long subtotalAmount;
    private Long discountAmount;
    private Long totalAmount;
    private OrderStatus status;

    public static OrderResponse from(Order order, List<OrderItem> items) {
        return OrderResponse.builder()
            .orderId(order.getId())
            .items(items.stream().map(OrderItemResponse::from).toList())
            .subtotalAmount(order.getSubtotalAmount())
            .discountAmount(order.getDiscountAmount())
            .totalAmount(order.getTotalAmount())
            .status(order.getStatus())
            .build();
    }
}
```

---

## 🔍 응답 데이터 정제

### null 값 제외
```java
// ✅ 좋은 예: null 제외
{
  "orderId": "ORDER-001",
  "totalAmount": 10000,
  "status": "PENDING"
  // couponId는 null이므로 제외
}

// ❌ 나쁜 예: null 포함
{
  "orderId": "ORDER-001",
  "totalAmount": 10000,
  "status": "PENDING",
  "couponId": null  // 불필요한 null
}
```

### Jackson 설정
```java
// application.yml
spring:
  jackson:
    default-property-inclusion: non_null  # null 필드 제외

// 또는 DTO에 어노테이션
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {
    // ...
}
```

---

## ✅ Pass 기준

### DTO 설계
- [ ] API별 전용 DTO 정의
- [ ] 공통 부분 Composition으로 재사용
- [ ] Static Factory Method 활용

### 입력 검증
- [ ] Controller에서 형식 검증 (@Valid)
- [ ] Entity에서 비즈니스 규칙 검증
- [ ] 검증 에러 메시지 명확

### 코드 품질
- [ ] DTO 변환 로직 명확 (from 메서드)
- [ ] null 값 처리 일관성
- [ ] 네이밍 일관성 (Request, Response)

---

## ❌ Fail 사유

### DTO Fail
- ❌ 모든 API에 하나의 DTO 사용
- ❌ Entity를 직접 응답으로 사용 (순환 참조 위험)
- ❌ DTO 변환 로직 누락

### 검증 Fail
- ❌ 입력 검증 부재
- ❌ 비즈니스 규칙을 Controller에서 검증
- ❌ 에러 메시지 부재

---

## 🎯 학습 체크리스트

### 이론 이해
- [ ] 레이어별 DTO 분리 원칙을 설명할 수 있다
- [ ] SRP와 DRY의 균형을 설명할 수 있다
- [ ] 형식 검증과 비즈니스 규칙 검증의 차이를 설명할 수 있다

### 실전 적용
- [ ] API별 전용 DTO를 작성할 수 있다
- [ ] Composition으로 공통 부분을 재사용할 수 있다
- [ ] Static Factory Method로 DTO를 변환할 수 있다

### 토론 주제
- "API마다 전용 DTO를 만들어야 하나요, 공통 DTO를 써야 하나요?"
- "Entity를 그대로 응답으로 사용하면 안 되는 이유는?"
- "검증 로직을 어디에 둬야 하나요?"

---

## 📚 참고 자료

- [DTO Pattern - Martin Fowler](https://martinfowler.com/eaaCatalog/dataTransferObject.html)
- [Bean Validation](https://beanvalidation.org/)
- CLAUDE.md - Q10. 레이어별로 DTO를 분리해야 하나요?

---

## 💡 실전 팁

### Request/Response DTO 네이밍
```java
// ✅ 좋은 예 (명확한 네이밍)
CreateOrderRequest
CreateOrderResponse
GetProductResponse
UpdateUserRequest

// ❌ 나쁜 예 (모호한 네이밍)
OrderDto
ProductDto
UserDto
```

### Lombok 활용
```java
@Getter
@AllArgsConstructor
@Builder  // 복잡한 DTO는 Builder
public class OrderResponse {
    private String orderId;
    private Long totalAmount;
    private OrderStatus status;
}
```

### 검증 어노테이션
```java
@NotNull      // null 불가
@NotBlank     // 빈 문자열 불가 (문자열 전용)
@NotEmpty     // 빈 컬렉션 불가 (컬렉션 전용)
@Size         // 크기 제한
@Min / @Max   // 숫자 범위
@Email        // 이메일 형식
@Pattern      // 정규식
```

---

**이전 학습**: [06. 테스트 전략](./06-testing-strategy.md)
**처음으로**: [README](./README.md)
