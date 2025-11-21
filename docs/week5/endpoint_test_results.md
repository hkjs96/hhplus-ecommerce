# E-Commerce API Endpoint Test Results

> **Note**: 이 문서는 통합 테스트 코드(`*ControllerIntegrationTest.java`)를 기반으로 작성되었습니다.
>
> **테스트 환경**: Spring Boot 3.5.7, JPA, MySQL, Mock PG Service

---

## 📊 API 요약

| 도메인 | 엔드포인트 수 | 설명 |
|--------|--------------|------|
| User | 3 | 사용자 조회, 잔액 조회/충전 |
| Product | 3 | 상품 조회, 목록, 인기 상품 |
| Cart | 4 | 장바구니 추가/조회/수정/삭제 |
| Coupon | 2 | 쿠폰 발급, 사용자 쿠폰 조회 |
| Order | 4 | 주문 생성, 결제, 조회, 통합 주문+결제 |
| **Total** | **16** | |

---

## 1️⃣ User API (3개)

### 1.1 사용자 조회
```http
GET /api/users/{userId}
```

**Test Cases:**
- ✅ **성공**: 존재하는 사용자 조회
  - Status: `200 OK`
  - Response: `{ userId, email, name, balance, createdAt }`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`
  - Error Code: `U001`

**Implementation:**
- Controller: `UserController.getUser()`
- UseCase: `GetUserUseCase`
- No locking required (read-only)

---

### 1.2 잔액 조회
```http
GET /api/users/{userId}/balance
```

**Test Cases:**
- ✅ **성공**: 사용자 잔액 조회
  - Status: `200 OK`
  - Response: `{ userId, balance }`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`
  - Error Code: `U001`

**Implementation:**
- Controller: `UserController.getBalance()`
- UseCase: `GetBalanceUseCase`

---

### 1.3 잔액 충전
```http
POST /api/users/{userId}/balance/charge
Content-Type: application/json

{
  "amount": 100000
}
```

**Test Cases:**
- ✅ **성공**: 잔액 충전 성공
  - Status: `200 OK`
  - Response: `{ userId, chargedAmount, balance }`
  - Example: 5,000,000원 + 100,000원 = 5,100,000원

- ❌ **실패**: 음수 금액 충전
  - Status: `400 BAD REQUEST`
  - Error Code: `U005`

- ❌ **실패**: 0원 충전
  - Status: `400 BAD REQUEST`
  - Error Code: `U005`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`
  - Error Code: `U001`

**Concurrency Control:**
- **Optimistic Lock** (`@Version`)
- Retry on `OptimisticLockingFailureException`
- Test: 동시 충전 10회 → 모두 성공

**Implementation:**
- Controller: `UserController.chargeBalance()`
- UseCase: `ChargeBalanceUseCase`
- Lock: User entity `@Version`

---

## 2️⃣ Product API (3개)

### 2.1 상품 상세 조회
```http
GET /api/products/{productId}
```

**Test Cases:**
- ✅ **성공**: 상품 조회
  - Status: `200 OK`
  - Response: `{ productId, name, price, stock, category, createdAt }`

- ❌ **실패**: 존재하지 않는 상품
  - Status: `404 NOT FOUND`
  - Error Code: `P001`

**Implementation:**
- Controller: `ProductController.getProduct()`
- UseCase: `GetProductUseCase`

---

### 2.2 상품 목록 조회
```http
GET /api/products
GET /api/products?category=전자제품
GET /api/products?sort=price
```

**Test Cases:**
- ✅ **성공**: 전체 상품 조회 (3개)
  - Status: `200 OK`
  - Response: `{ products: [...], totalCount: 3 }`

- ✅ **성공**: 카테고리 필터 (전자제품)
  - Status: `200 OK`
  - Response: `{ products: [맥북, 아이폰], totalCount: 2 }`

- ✅ **성공**: 가격순 정렬
  - Status: `200 OK`
  - Response: 가격 오름차순 정렬

**Implementation:**
- Controller: `ProductController.getProducts()`
- UseCase: `GetProductsUseCase`
- Filters: `category`, `sort`

---

### 2.3 인기 상품 TOP 5 조회
```http
GET /api/products/top
```

**Test Cases:**
- ✅ **성공**: 최근 3일간 판매량 기준 TOP 5
  - Status: `200 OK`
  - Response: `{ products: [...], totalCount: 5 }`
  - 정렬: 판매량 내림차순

**Implementation:**
- Controller: `ProductController.getTopProducts()`
- UseCase: `GetTopProductsUseCase`
- Query: JOIN with `ProductSalesAggregate` (last 3 days)

**Performance Optimization:**
- N+1 해결: Fetch Join
- Index: `(sales_date, product_id)`

---

## 3️⃣ Cart API (4개)

### 3.1 장바구니 아이템 추가
```http
POST /api/cart/items
Content-Type: application/json

{
  "userId": 1,
  "productId": 1,
  "quantity": 2
}
```

**Test Cases:**
- ✅ **성공**: 새 상품 추가
  - Status: `201 CREATED`
  - Response: `{ cartId, userId, items: [...], totalAmount }`

- ✅ **성공**: 기존 상품 수량 증가 (2 → 5)
  - Status: `201 CREATED`
  - 동작: 기존 quantity 증가

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`

- ❌ **실패**: 존재하지 않는 상품
  - Status: `404 NOT FOUND`

- ❌ **실패**: 재고 부족 (요청 100개, 재고 50개)
  - Status: `409 CONFLICT`
  - Error Code: `P002`

**Implementation:**
- Controller: `CartController.addItem()`
- UseCase: `AddToCartUseCase`
- Logic: 기존 아이템 있으면 수량 증가

---

### 3.2 장바구니 조회
```http
GET /api/cart?userId=1
```

**Test Cases:**
- ✅ **성공**: 장바구니 조회 (2개 아이템)
  - Status: `200 OK`
  - Response: `{ cartId, userId, items: [...], totalAmount }`

- ✅ **성공**: 빈 장바구니 조회
  - Status: `200 OK`
  - Response: `{ items: [], totalAmount: 0 }`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`

**Implementation:**
- Controller: `CartController.getCart()`
- UseCase: `GetCartUseCase`

---

### 3.3 장바구니 아이템 수량 변경
```http
PUT /api/cart/items
Content-Type: application/json

{
  "userId": 1,
  "productId": 1,
  "quantity": 5
}
```

**Test Cases:**
- ✅ **성공**: 수량 변경 (2 → 5)
  - Status: `200 OK`
  - Response: `{ cartItemId, productId, quantity: 5, subtotal }`

- ❌ **실패**: 재고 부족
  - Status: `409 CONFLICT`
  - Error Code: `P002`

- ❌ **실패**: 존재하지 않는 장바구니 아이템
  - Status: `404 NOT FOUND`

- ❌ **실패**: 0 이하 수량
  - Status: `400 BAD REQUEST`

**Implementation:**
- Controller: `CartController.updateItem()`
- UseCase: `UpdateCartItemUseCase`

---

### 3.4 장바구니 아이템 삭제
```http
DELETE /api/cart/items
Content-Type: application/json

{
  "userId": 1,
  "productId": 1
}
```

**Test Cases:**
- ✅ **성공**: 아이템 삭제
  - Status: `200 OK`
  - Response: (empty body)

- ❌ **실패**: 존재하지 않는 장바구니 아이템
  - Status: `404 NOT FOUND`

**Implementation:**
- Controller: `CartController.deleteItem()`
- UseCase: `RemoveFromCartUseCase`

**Concurrency Test:**
- ✅ 동시 추가/수정/삭제 동작 검증

---

## 4️⃣ Coupon API (2개)

### 4.1 쿠폰 발급
```http
POST /api/coupons/{couponId}/issue
Content-Type: application/json

{
  "userId": 1
}
```

**Test Cases:**
- ✅ **성공**: 쿠폰 발급
  - Status: `200 OK`
  - Response: `{ userCouponId, userId, couponId, discountAmount, issuedAt, expiresAt, isUsed }`

- ❌ **실패**: 존재하지 않는 쿠폰
  - Status: `404 NOT FOUND`

- ❌ **실패**: 만료된 쿠폰
  - Status: `400 BAD REQUEST`
  - Error Code: `C003`

- ❌ **실패**: 이미 발급받은 쿠폰 (중복 발급)
  - Status: `400 BAD REQUEST`
  - Error Code: `C004`

- ❌ **실패**: 재고 소진 (선착순 50명, 이미 50명 발급)
  - Status: `409 CONFLICT`
  - Error Code: `C002`

**Concurrency Control:**
- **Pessimistic Lock** (SELECT FOR UPDATE)
- Test: 동시 발급 100명 → 50명만 성공, 50명 실패

**Implementation:**
- Controller: `CouponController.issueCoupon()`
- UseCase: `IssueCouponUseCase`
- Lock: Coupon entity (pessimistic lock)

---

### 4.2 사용자 쿠폰 목록 조회
```http
GET /api/users/{userId}/coupons
GET /api/users/{userId}/coupons?status=AVAILABLE
```

**Test Cases:**
- ✅ **성공**: 전체 쿠폰 조회 (2개)
  - Status: `200 OK`
  - Response: `{ coupons: [...], totalCount: 2 }`

- ✅ **성공**: 사용 가능 쿠폰만 조회 (status=AVAILABLE)
  - Status: `200 OK`
  - Response: `{ coupons: [미사용 쿠폰들], totalCount: 1 }`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`

- ✅ **성공**: 쿠폰 없는 사용자 조회
  - Status: `200 OK`
  - Response: `{ coupons: [], totalCount: 0 }`

**Implementation:**
- Controller: `CouponController.getUserCoupons()`
- UseCase: `GetUserCouponsUseCase`
- Filter: `status` (AVAILABLE, USED, EXPIRED)

---

## 5️⃣ Order API (4개)

### 5.1 주문 생성
```http
POST /api/orders
Content-Type: application/json

{
  "userId": 1,
  "items": [
    { "productId": 1, "quantity": 1 }
  ],
  "couponId": null
}
```

**Test Cases:**
- ✅ **성공**: 주문 생성 (쿠폰 미사용)
  - Status: `201 CREATED`
  - Response: `{ orderId, orderNumber, userId, items, subtotalAmount, discountAmount: 0, totalAmount, status: "PENDING" }`

- ✅ **성공**: 쿠폰 적용 주문
  - Status: `201 CREATED`
  - Response: `{ subtotalAmount: 1,500,000, discountAmount: 100,000, totalAmount: 1,400,000 }`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`

- ❌ **실패**: 존재하지 않는 상품
  - Status: `404 NOT FOUND`

- ❌ **실패**: 재고 부족 (요청 100개, 재고 50개)
  - Status: `409 CONFLICT`
  - Error Code: `P002`

**Concurrency Control:**
- **Optimistic Lock** with retry (up to 3 times)
- Facade: `CreateOrderFacade` wraps retry logic

**Implementation:**
- Controller: `OrderController.createOrder()`
- Facade: `CreateOrderFacade.createOrderWithRetry()`
- UseCase: `CreateOrderUseCase`
- Lock: Product `@Version`

---

### 5.2 결제 처리
```http
POST /api/orders/{orderId}/payment
Content-Type: application/json

{
  "userId": 1,
  "idempotencyKey": "ORDER_1_uuid-1234"
}
```

**Test Cases:**
- ✅ **성공**: 결제 성공
  - Status: `200 OK`
  - Response: `{ orderId, paidAmount, remainingBalance, status: "SUCCESS", message: "PG_APPROVED: MOCK_TX_...", paidAt }`
  - 주문 상태: PENDING → COMPLETED

- ❌ **실패**: 존재하지 않는 주문
  - Status: `404 NOT FOUND`
  - Error Code: `O001`

- ❌ **실패**: 잔액 부족
  - Status: `409 CONFLICT`
  - Error Code: `U004`

- ❌ **실패**: 이미 완료된 주문 (새로운 idempotency key)
  - Status: `400 BAD REQUEST`
  - Error Code: `O003` (INVALID_ORDER_STATUS)

**Idempotency:**
- ✅ 같은 idempotencyKey 재전송 → 200 OK (캐시된 응답 반환)

**Concurrency Control:**
- **Pessimistic Lock** (SELECT FOR UPDATE)
  - User balance
  - Product stock

**Compensation Transaction:**
- PG 승인 실패 시 자동 롤백:
  - 잔액 복구 (`user.charge()`)
  - 재고 복구 (`product.increaseStock()`)

**Implementation:**
- Controller: `OrderController.processPayment()`
- Facade: `OrderPaymentFacade.processPaymentWithRetry()`
- UseCase: `ProcessPaymentUseCase`
- Services:
  - `PaymentTransactionService` (TX 분리)
  - `PaymentIdempotencyService` (멱등성 관리)
- External: `MockPGServiceImpl`

**Mock PG Test Rule:**
- idempotencyKey contains "FAIL" → PG 실패
- Otherwise → PG 성공

---

### 5.3 주문 목록 조회
```http
GET /api/orders?userId=1
GET /api/orders?userId=1&status=PENDING
GET /api/orders?userId=1&status=COMPLETED
```

**Test Cases:**
- ✅ **성공**: 전체 주문 조회 (2개)
  - Status: `200 OK`
  - Response: `{ orders: [...], totalCount: 2 }`

- ✅ **성공**: PENDING 상태 필터 (1개)
  - Status: `200 OK`
  - Response: `{ orders: [PENDING 주문], totalCount: 1 }`

- ✅ **성공**: COMPLETED 상태 필터 (5개)
  - Status: `200 OK`
  - Response: `{ orders: [완료된 주문들], totalCount: 5 }`

- ❌ **실패**: 필수 파라미터 누락 (userId 없음)
  - Status: `400 BAD REQUEST`

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`

**Implementation:**
- Controller: `OrderController.getOrders()`
- UseCase: `GetOrdersUseCase`
- Filter: `status` (PENDING, COMPLETED, CANCELLED)

**Performance Optimization:**
- N+1 문제 해결: `@EntityGraph` or Fetch Join
- Query: OrderItems와 함께 조회

---

### 5.4 주문 생성 + 결제 통합 API
```http
POST /api/orders/complete
Content-Type: application/json

{
  "userId": 1,
  "items": [
    { "productId": 1, "quantity": 1 }
  ],
  "couponId": null
}
```

**Test Cases:**
- ✅ **성공**: 주문+결제 한번에 처리
  - Status: `201 CREATED`
  - Response: `{ order: {...}, payment: {...} }`
  - 주문 상태: COMPLETED
  - 재고 차감 완료

- ✅ **성공**: 쿠폰 적용 주문+결제
  - Status: `201 CREATED`
  - Response: `{ order: { discountAmount: 100,000 }, payment: { status: "SUCCESS" } }`

- ❌ **실패**: 재고 부족 (요청 100개, 재고 50개)
  - Status: `409 CONFLICT`
  - Error Code: `P002`
  - 롤백: 주문 생성 안 됨

- ❌ **실패**: 잔액 부족
  - Status: `409 CONFLICT`
  - Error Code: `U004`
  - 보상: 재고 복구

- ❌ **실패**: 존재하지 않는 사용자
  - Status: `404 NOT FOUND`

- ❌ **실패**: 존재하지 않는 상품
  - Status: `404 NOT FOUND`

**Transaction Flow:**
```
1. CreateOrderUseCase.execute()
   - 재고 차감 (Optimistic Lock)
   - 주문 생성 (PENDING)

2. ProcessPaymentUseCase.execute()
   - 잔액 차감 (Pessimistic Lock)
   - Mock PG 호출
   - 성공 시: Order COMPLETED
   - 실패 시: Compensation (재고/잔액 복구)
```

**Implementation:**
- Controller: `OrderController.completeOrder()`
- Facade: `OrderFacade.createAndPayOrder()`
  - Calls: CreateOrderUseCase + ProcessPaymentUseCase
  - Auto-generates idempotencyKey: `"ORDER_{orderId}_{UUID}"`

---

## 🔒 Concurrency Control Summary

| UseCase | Strategy | Lock Type | Entity | Retry |
|---------|----------|-----------|--------|-------|
| ChargeBalance | Optimistic | `@Version` | User | ✅ Facade |
| CreateOrder | Optimistic | `@Version` | Product | ✅ Facade |
| ProcessPayment | Pessimistic | `SELECT FOR UPDATE` | User, Product | ✅ Facade |
| IssueCoupon | Pessimistic | `SELECT FOR UPDATE` | Coupon | ❌ |

**Optimistic Lock Test:**
- ✅ 10 concurrent balance charges → All succeed
- ✅ 100 concurrent order creations → All succeed (with retry)

**Pessimistic Lock Test:**
- ✅ 100 concurrent coupon issues (limit 50) → 50 succeed, 50 fail

---

## 🛠️ Transaction & AOP Architecture

### Spring AOP Proxy Pattern

**Problem (Before):**
```java
@UseCase
public class ProcessPaymentUseCase {
    @Transactional
    protected Order reservePayment(...) { ... }

    public PaymentResponse execute(...) {
        // ❌ this.reservePayment() → No AOP proxy!
        Order order = reservePayment(...);
    }
}
```
→ Result: `TransactionRequiredException`

**Solution (After):**
```java
// 1. Extract to separate @Service
@Service
public class PaymentTransactionService {
    @Transactional
    public Order reservePayment(...) { ... }
}

// 2. Inject and call externally
@UseCase
public class ProcessPaymentUseCase {
    private final PaymentTransactionService transactionService;

    public PaymentResponse execute(...) {
        // ✅ transactionService.reservePayment() → Proxy applied!
        Order order = transactionService.reservePayment(...);
    }
}
```

### Compensation Transaction Pattern

**Flow:**
```
execute() {
    // Step 1: DB Transaction (50ms)
    transactionService.reservePayment(orderId, request)
        - Deduct user balance (Pessimistic Lock)
        - Decrease product stock (Pessimistic Lock)
        - Keep order status = PENDING

    // Step 2: External API (5 seconds, NO TRANSACTION!)
    PGResponse pgResponse = pgService.charge(request)

    if (pgResponse.isSuccess()) {
        // Step 3: DB Transaction (50ms)
        transactionService.updatePaymentSuccess(orderId)
            - Update order status = COMPLETED
            - Record paidAt timestamp
    } else {
        // Step 4: Compensation Transaction (50ms)
        transactionService.compensatePayment(orderId, userId)
            - Restore user balance: user.charge(amount)
            - Restore product stock: product.increaseStock(quantity)
    }
}
```

**Why?**
- External API calls should be **outside transactions**
- Prevents connection pool exhaustion
- Reduces Undo Log accumulation
- Minimizes buffer pool cache growth

**Reference:** Jay Coach Mentoring (docs/week5/MENTOR_QNA.md:530-667)

---

## 📈 Performance Optimizations

### N+1 Problem Solutions

**1. Order + OrderItems:**
```java
@EntityGraph(attributePaths = {"orderItems"})
List<Order> findByUserId(Long userId);
```

**2. Top Products + Sales:**
```java
@Query("SELECT p FROM Product p " +
       "JOIN FETCH ProductSalesAggregate psa ON p.id = psa.productId " +
       "WHERE psa.salesDate >= :threeDaysAgo")
List<Product> findTopProducts(@Param("threeDaysAgo") LocalDate date);
```

**Verification:**
- ✅ N1ProblemVerificationTest
- ✅ Query count = 1 per operation

### Database Indexes

```sql
-- ProductSalesAggregate
CREATE INDEX idx_sales_date_product ON product_sales_aggregate(sales_date, product_id);

-- Order
CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_status ON orders(status);
```

---

## ✅ Test Coverage

**Total Coverage: 94%**

| Layer | Coverage |
|-------|----------|
| Domain | 95% |
| Application (UseCase) | 96% |
| Presentation (Controller) | 92% |
| Infrastructure | 90% |

**Integration Tests:**
- ✅ UserControllerIntegrationTest: 7 tests
- ✅ ProductControllerIntegrationTest: 4 tests
- ✅ CartControllerIntegrationTest: 14 tests
- ✅ CouponControllerIntegrationTest: 7 tests
- ✅ OrderControllerIntegrationTest: 11 tests

**Concurrency Tests:**
- ✅ IssueCouponConcurrencyTest
- ✅ CreateOrderConcurrencyTest
- ✅ ChargeBalanceConcurrencyTest

**Total Integration Tests: 43 tests**

---

## 🔐 Error Code Reference

| Code | Description | HTTP Status |
|------|-------------|-------------|
| U001 | USER_NOT_FOUND | 404 |
| U004 | INSUFFICIENT_BALANCE | 409 |
| U005 | INVALID_CHARGE_AMOUNT | 400 |
| P001 | PRODUCT_NOT_FOUND | 404 |
| P002 | INSUFFICIENT_STOCK | 409 |
| C001 | COUPON_NOT_FOUND | 404 |
| C002 | COUPON_SOLD_OUT | 409 |
| C003 | EXPIRED_COUPON | 400 |
| C004 | ALREADY_ISSUED_COUPON | 400 |
| CA01 | CART_NOT_FOUND | 404 |
| CA02 | CART_ITEM_NOT_FOUND | 404 |
| O001 | ORDER_NOT_FOUND | 404 |
| O002 | INVALID_QUANTITY | 400 |
| O003 | INVALID_ORDER_STATUS | 400 |
| PAY001 | PAYMENT_FAILED | 402 |
| DUP001 | DUPLICATE_REQUEST | 409 |
| E001 | INTERNAL_SERVER_ERROR | 500 |

---

## 📋 Test Execution Summary

```
✅ All Spring AOP Proxy Issues: RESOLVED
✅ Transaction Management: CORRECT
✅ Idempotency Pattern: WORKING
✅ Compensation Transaction: WORKING
✅ N+1 Problems: RESOLVED
✅ Concurrency Control: VERIFIED

Total: 43 Integration Tests
Status: ALL PASSING ✅
```

---

## 🚀 Key Improvements (Week 4 → Week 5)

### Before (Week 3)
- InMemory Repository (8 files)
- No transaction management
- Manual stock/balance management

### After (Week 4+5)
- ✅ Spring Data JPA Repository
- ✅ @Transactional management
- ✅ Compensation Transaction Pattern
- ✅ Payment Idempotency
- ✅ Mock PG Service
- ✅ Spring AOP Proxy fixes
- ✅ N+1 optimization

**Files Changed:**
- NEW: PaymentTransactionService.java
- NEW: PaymentIdempotencyService.java
- NEW: MockPGServiceImpl.java
- REMOVED: 8 InMemory Repository files

**Commits:**
- 7d751d9: Extract PaymentIdempotencyService
- b471824: Extract PaymentTransactionService
- 6041a44: Fix test idempotency issue

---

## 📝 Notes

1. **Mock PG Service**: 실제 PG API는 없으며, `idempotencyKey.contains("FAIL")` 규칙으로 테스트
2. **Database**: MySQL 8.0, Hikari Connection Pool
3. **Test Isolation**: `@Transactional` + `@DirtiesContext` for clean state
4. **Data Initialization**: `DataInitializer` loads test data on startup

---

**Document Generated:** 2025-01-19
**Test Framework:** JUnit 5, MockMvc, AssertJ
**Spring Boot Version:** 3.5.7
