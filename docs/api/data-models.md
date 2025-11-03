# 데이터 모델 설계

## 도메인 모델 개요

이커머스 시스템의 핵심 도메인:
- **Product**: 상품 정보 관리
- **Stock**: 재고 현황 관리
- **StockHistory**: 재고 변동 이력 추적
- **Order**: 주문 생성 및 상태 관리
- **Payment**: 결제 처리
- **Coupon**: 쿠폰 발급 및 사용
- **User**: 사용자 및 잔액 관리

---

## 엔티티 정의

### Product (상품)
```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    private String id;              // PK
    private String name;            // 상품명
    private String description;     // 상품 설명
    private BigDecimal price;       // 가격
    private String category;        // 카테고리
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**비즈니스 규칙:**
- 가격은 양수여야 함
- 재고 정보는 별도의 Stock 테이블에서 관리

---

### Stock (재고 현황)
```java
@Entity
@Table(name = "stock",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "warehouse_id"})
    }
)
public class Stock {
    @Id
    private String id;              // PK
    private String productId;       // FK -> Product
    private Integer quantity;       // 현재 재고 수량
    private String warehouseId;     // 창고 ID (기본값: "DEFAULT", 향후 확장 대비)
    private LocalDateTime updatedAt;

    @Version
    private Long version;           // Optimistic Lock for 동시성 제어
}
```

**비즈니스 규칙:**
- quantity는 0 이상이어야 함
- 재고 차감 시 Optimistic Lock을 통한 동시성 제어
- 하나의 상품은 창고별로 하나의 재고 레코드만 가짐 (UK: product_id + warehouse_id)
- 재고 변동 시 반드시 StockHistory에 이력 기록

**설계 의도:**
- Product와 Stock 분리로 재고 관리의 독립성 확보
- 향후 다중 창고 확장 가능
- Pessimistic Lock 대신 Optimistic Lock 사용으로 성능 개선

---

### StockHistory (재고 변동 이력)
```java
@Entity
@Table(name = "stock_history")
public class StockHistory {
    @Id
    private String id;                      // PK
    private String stockId;                 // Stock ID (FK 제약조건 없음)
    private String productId;               // Product ID (FK 제약조건 없음)
    private StockChangeType type;           // IN(입고), OUT(출고), CANCEL(취소), ADJUST(조정)
    private Integer quantityBefore;         // 변경 전 수량
    private Integer quantityChange;         // 변경량 (+/-)
    private Integer quantityAfter;          // 변경 후 수량
    private String referenceType;           // 참조 타입 (ORDER, PURCHASE, RETURN, ADJUSTMENT)
    private String referenceId;             // 참조 ID (예: orderId)
    private String reason;                  // 변경 사유
    private LocalDateTime createdAt;
}
```

**StockChangeType:**
- `IN`: 입고 (재고 증가)
- `OUT`: 출고/판매 (재고 감소)
- `CANCEL`: 주문 취소/반품 (재고 복구)
- `ADJUST`: 재고 조정 (재고 실사 등)

**비즈니스 규칙:**
- INSERT ONLY 테이블 (수정/삭제 불가)
- 외래키 제약조건 없음 (조회 목적, 성능 최적화)
- 애플리케이션 레벨에서 데이터 무결성 보장
- 모든 재고 변동은 반드시 이력으로 기록

**설계 의도:**
- 조회 전용 테이블로 FK 제약조건 없이 인덱스만 설정
- 재고 변동 추적 및 감사(Audit) 목적
- 재고 불일치 발생 시 디버깅 및 원인 파악 가능

---

### Cart (장바구니)
```java
@Entity
@Table(name = "carts")
public class Cart {
    @Id
    private String id;              // PK (cartId)
    private String userId;          // FK -> User
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "cart")
    private List<CartItem> items;
}
```

**비즈니스 규칙:**
- 사용자당 하나의 장바구니
- 장바구니는 사용자가 로그인하면 자동 생성
- 일정 기간 미사용 시 자동 삭제 (선택)

**설계 의도:**
- 주문 전에 여러 상품을 담아두는 임시 저장소
- 재고 확인은 주문 시점에 수행

---

### CartItem (장바구니 상품)
```java
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    private String id;              // PK
    private String cartId;          // FK -> Cart
    private String productId;       // FK -> Product
    private Integer quantity;       // 수량
    private LocalDateTime addedAt;
}
```

**비즈니스 규칙:**
- quantity는 1 이상이어야 함
- 같은 상품 중복 추가 시 수량만 증가
- 재고 초과 시 추가 불가

**설계 의도:**
- Cart와 Product의 다대다 관계를 풀어낸 중간 테이블
- 주문 생성 시 CartItem을 OrderItem으로 변환

---

### Order (주문)
```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private String id;                  // PK
    private String userId;              // FK -> User
    private BigDecimal subtotalAmount;  // 소계
    private BigDecimal discountAmount;  // 할인 금액
    private BigDecimal totalAmount;     // 최종 금액
    private OrderStatus status;         // PENDING, PAID, CANCELLED
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
}
```

**비즈니스 규칙:**
- totalAmount = subtotalAmount - discountAmount
- 결제 완료 시에만 재고 차감
- 결제 실패 시 상태를 CANCELLED로 변경

---

### OrderItem (주문 상세)
```java
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    private String id;              // PK
    private String orderId;         // FK -> Order
    private String productId;       // FK -> Product
    private Integer quantity;       // 수량
    private BigDecimal unitPrice;   // 단가
    private BigDecimal subtotal;    // 소계
}
```

**비즈니스 규칙:**
- subtotal = unitPrice * quantity
- quantity는 1 이상이어야 함

---

### Coupon (쿠폰 마스터)
```java
@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    private String id;              // PK
    private String name;            // 쿠폰명
    private Integer discountRate;   // 할인율 (%)
    private Integer totalQuantity;  // 총 수량
    private Integer issuedQuantity; // 발급된 수량
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
```

**비즈니스 규칙:**
- issuedQuantity <= totalQuantity
- 발급 시 issuedQuantity 증가 (동시성 제어 필요)
- discountRate는 0~100 사이

---

### UserCoupon (사용자 쿠폰)
```java
@Entity
@Table(name = "user_coupons")
public class UserCoupon {
    @Id
    private String id;              // PK
    private String userId;          // FK -> User
    private String couponId;        // FK -> Coupon
    private CouponStatus status;    // AVAILABLE, USED, EXPIRED
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;
}
```

**비즈니스 규칙:**
- 한 사용자는 같은 쿠폰을 1번만 발급받을 수 있음
- 사용 후 status를 USED로 변경
- 만료일이 지나면 status를 EXPIRED로 변경

---

### User (사용자)
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;              // PK
    private String email;           // 이메일 (UK)
    private String username;        // 사용자명
    private BigDecimal balance;     // 포인트 잔액 (내부 포인트 시스템)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**비즈니스 규칙:**
- balance는 0 이상이어야 함
- **포인트 시스템**: PG 없이 내부 포인트로만 운영
  - 사용자는 미리 포인트를 충전
  - 결제는 충전된 포인트로만 가능
- 결제 시 포인트 차감 (동시성 제어 필요)

**설계 의도:**
- 외부 PG 연동 없이 간단한 포인트 시스템으로 구현
- 향후 PG 연동 시 Payment 엔티티를 별도로 분리 가능

---

## 관계 정의

### 1:N 관계
- **Product** 1 → N **Stock**: 한 상품은 여러 창고에 재고 보유 (현재는 DEFAULT 창고 1개)
- **Stock** 1 → N **StockHistory**: 한 재고는 여러 변동 이력 보유 (FK 제약조건 없음)
- **User** 1 → N **Cart**: 한 사용자는 하나의 장바구니 보유
- **User** 1 → N **Order**: 한 사용자는 여러 주문을 생성
- **User** 1 → N **UserCoupon**: 한 사용자는 여러 쿠폰 소유
- **Cart** 1 → N **CartItem**: 한 장바구니는 여러 상품 포함
- **Order** 1 → N **OrderItem**: 한 주문은 여러 상품 포함
- **Coupon** 1 → N **UserCoupon**: 한 쿠폰은 여러 사용자에게 발급

### N:1 관계
- **CartItem** N → 1 **Product**: 여러 장바구니 항목이 하나의 상품 참조
- **OrderItem** N → 1 **Product**: 여러 주문 항목이 하나의 상품 참조

### 주의사항
- **StockHistory**는 조회 목적으로만 사용되며, FK 제약조건을 설정하지 않음
- 대신 인덱스를 통해 조회 성능 최적화
- 데이터 무결성은 애플리케이션 레벨에서 보장

---

## 인덱스 전략

### 필수 인덱스
```sql
-- 상품 조회 최적화
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_created_at ON products(created_at);

-- 재고 조회 최적화
CREATE INDEX idx_stock_product_id ON stock(product_id);
CREATE UNIQUE INDEX idx_stock_product_warehouse ON stock(product_id, warehouse_id);

-- 재고 이력 조회 최적화 (FK 제약조건 없이 인덱스만 설정)
CREATE INDEX idx_stock_history_stock_id ON stock_history(stock_id);
CREATE INDEX idx_stock_history_product_id ON stock_history(product_id);
CREATE INDEX idx_stock_history_reference ON stock_history(reference_type, reference_id);
CREATE INDEX idx_stock_history_created_at ON stock_history(created_at DESC);

-- 장바구니 조회 최적화
CREATE INDEX idx_carts_user_id ON carts(user_id);
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);

-- 주문 조회 최적화
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- 통계 쿼리 최적화 (인기 상품)
CREATE INDEX idx_orders_paid_at ON orders(paid_at);
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- 쿠폰 조회 최적화
CREATE INDEX idx_user_coupons_user_status ON user_coupons(user_id, status);
CREATE INDEX idx_user_coupons_expires_at ON user_coupons(expires_at);
```

---

## 동시성 제어 전략

### 재고 차감 (Optimistic Lock)
```java
@Entity
public class Stock {
    @Version
    private Long version;
    // ...
}

// Repository
public interface StockRepository {
    Stock findByProductId(String productId);

    // Optimistic Lock을 통한 재고 차감
    // Version mismatch 시 OptimisticLockException 발생
    @Modifying
    @Query("UPDATE Stock s SET s.quantity = s.quantity - :quantity, s.updatedAt = :now " +
           "WHERE s.productId = :productId AND s.quantity >= :quantity")
    int decreaseStock(@Param("productId") String productId,
                      @Param("quantity") int quantity,
                      @Param("now") LocalDateTime now);
}
```

**재고 차감 전략:**
- Optimistic Lock 사용으로 성능 최적화
- 재고 차감 실패 시 애플리케이션에서 재시도 로직 구현
- 재고 변동 시 StockHistory에 이력 기록 필수

### 쿠폰 발급 (Optimistic Lock)
```java
@Entity
public class Coupon {
    @Version
    private Long version;
    // ...
}
```

### 포인트 차감 (Pessimistic Lock)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
User findByIdWithLock(@Param("id") String id);
```

**포인트 차감 전략:**
- Pessimistic Lock 사용으로 정확성 보장
- 포인트는 PG 없이 내부 시스템으로만 관리
- 충돌 빈도가 낮아 성능 영향 최소화

**동시성 제어 정책 요약:**

| 엔티티 | 락 방식 | 이유 |
|--------|---------|------|
| Stock | Optimistic Lock | 높은 동시성, 재시도 가능, 성능 우선 |
| Coupon | Optimistic Lock | 선착순 발급, 충돌 시 명확한 에러 응답 |
| User (포인트) | Pessimistic Lock | 포인트 정확성 최우선, 충돌 빈도 낮음 |

---

## 참고: 주요 쿼리 예시

### 인기 상품 통계 (배치 집계)
```sql
-- 최근 3일 인기 상품 Top 5
SELECT
    p.id,
    p.name,
    SUM(oi.quantity) as sales_count,
    SUM(oi.subtotal) as revenue
FROM products p
JOIN order_items oi ON p.id = oi.product_id
JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'PAID'
  AND o.paid_at >= NOW() - INTERVAL 3 DAY
GROUP BY p.id, p.name
ORDER BY sales_count DESC
LIMIT 5;
```

### 재고 조회 (상품별 재고 현황)
```sql
-- 상품별 현재 재고 조회
SELECT
    p.id,
    p.name,
    s.quantity,
    s.warehouse_id,
    s.updated_at
FROM products p
LEFT JOIN stock s ON p.id = s.product_id
WHERE p.id = 'P001';
```

### 재고 변동 이력 조회
```sql
-- 특정 상품의 최근 재고 변동 이력 (최근 30일)
SELECT
    sh.created_at,
    sh.type,
    sh.quantity_before,
    sh.quantity_change,
    sh.quantity_after,
    sh.reference_type,
    sh.reference_id,
    sh.reason
FROM stock_history sh
WHERE sh.product_id = 'P001'
  AND sh.created_at >= NOW() - INTERVAL 30 DAY
ORDER BY sh.created_at DESC
LIMIT 100;
```

### 재고 차감 및 이력 기록 (트랜잭션)
```java
@Transactional
public void decreaseStock(String productId, int quantity, String orderId) {
    // 1. 재고 차감 (Optimistic Lock)
    Stock stock = stockRepository.findByProductId(productId);

    if (stock.getQuantity() < quantity) {
        throw new InsufficientStockException();
    }

    int quantityBefore = stock.getQuantity();
    stock.decreaseQuantity(quantity);
    stockRepository.save(stock);

    // 2. 재고 변동 이력 기록
    StockHistory history = StockHistory.builder()
        .stockId(stock.getId())
        .productId(productId)
        .type(StockChangeType.OUT)
        .quantityBefore(quantityBefore)
        .quantityChange(-quantity)
        .quantityAfter(stock.getQuantity())
        .referenceType("ORDER")
        .referenceId(orderId)
        .reason("주문에 따른 재고 차감")
        .build();

    stockHistoryRepository.save(history);
}
```
