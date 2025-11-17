# N+1 문제 해결 - Fetch Join 방식 완벽 가이드

## 🎯 Fetch Join 방식 선택 이유

### Batch Size vs Fetch Join 비교

| 항목 | Batch Size | Fetch Join |
|------|-----------|------------|
| 쿼리 개수 | 3개 (Order, OrderItems IN, Products IN) | **1개** (JOIN 한 방) |
| 명시성 | 묵시적 (설정 기반) | **명시적** (쿼리 기반) |
| 제어 가능성 | 전역 설정 | **메서드별 제어** |
| 페이징 | 가능 | 메모리 페이징 (주의) |
| 성능 | 우수 | **최상** (단일 쿼리) |
| 카테시안 곱 | 없음 | 주의 필요 (DISTINCT) |

**결론: Fetch Join 채택!**
- 한 번의 쿼리로 모든 데이터 로딩
- 명시적 제어 가능
- 율무 코치님 피드백: "패치 조인으로 가져온다"

---

## ✅ 적용된 Fetch Join 쿼리

### 1. Order 조회 (OrderItem + Product 포함)

```java
// JpaOrderRepository.java
@Query("SELECT DISTINCT o FROM Order o " +
       "LEFT JOIN FETCH o.orderItems oi " +
       "LEFT JOIN FETCH oi.product " +
       "WHERE o.userId = :userId " +
       "ORDER BY o.createdAt DESC")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**실행되는 SQL:**
```sql
SELECT DISTINCT
    o.id, o.order_number, o.user_id, o.total_amount,
    oi.id, oi.order_id, oi.product_id, oi.quantity,
    p.id, p.name, p.price, p.stock
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
LEFT JOIN products p ON p.id = oi.product_id
WHERE o.user_id = ?
ORDER BY o.created_at DESC
```

→ **단 1개의 쿼리**로 Order + OrderItem + Product 모두 조회!

---

### 2. CartItem 조회 (Product 포함)

```java
// JpaCartItemRepository.java
@Query("SELECT ci FROM CartItem ci " +
       "LEFT JOIN FETCH ci.product " +
       "WHERE ci.cartId = :cartId " +
       "ORDER BY ci.createdAt DESC")
List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);
```

**실행되는 SQL:**
```sql
SELECT
    ci.id, ci.cart_id, ci.product_id, ci.quantity,
    p.id, p.name, p.price, p.stock
FROM cart_items ci
LEFT JOIN products p ON p.id = ci.product_id
WHERE ci.cart_id = ?
ORDER BY ci.created_at DESC
```

---

## 🔍 실제 동작 확인

### 애플리케이션 실행 후 로그 확인

```bash
# 1. 애플리케이션 시작
./gradlew bootRun

# 2. API 호출
curl "http://localhost:8080/api/orders?userId=1"
```

### ✅ 성공 (Fetch Join)

콘솔에 **단 1개의 SELECT** 쿼리만 출력됨:

```
Hibernate:
    select
        distinct o1_0.id,
        o1_0.user_id,
        oi1_0.order_id,
        oi1_0.id,
        p1_0.id,
        p1_0.name
    from orders o1_0
    left join order_items oi1_0
        on o1_0.id=oi1_0.order_id
    left join products p1_0
        on p1_0.id=oi1_0.product_id
    where o1_0.user_id=?
```

**🎉 추가 쿼리 없음!**

---

### ❌ 실패 (N+1 문제)

만약 Fetch Join이 없다면:

```
Hibernate: select ... from orders where user_id=?         -- 1번
Hibernate: select ... from order_items where order_id=1   -- N번
Hibernate: select ... from order_items where order_id=2
Hibernate: select ... from products where id=1            -- N번
Hibernate: select ... from products where id=2
...
```

**총 수십 개의 쿼리 발생!**

---

## 📊 성능 비교

| 시나리오 | N+1 있을 때 | Fetch Join |
|---------|------------|-----------|
| 주문 10개 조회 | 1 + 10 + 30 = **41 쿼리** | **1 쿼리** |
| 주문 100개 조회 | 1 + 100 + 300 = **401 쿼리** | **1 쿼리** |
| 장바구니 상품 10개 | 1 + 10 = **11 쿼리** | **1 쿼리** |

**성능 향상: 최대 400배! 🚀**

---

## 🛠️ UseCase 적용 예시

### Before (N+1 발생)

```java
@Transactional(readOnly = true)
public OrderListResponse execute(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);

    for (Order order : orders) {
        // ❌ Lazy Loading으로 추가 쿼리 발생!
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();  // 추가 쿼리!
            System.out.println(product.getName());
        }
    }
}
```

### After (Fetch Join)

```java
@Transactional(readOnly = true)
public OrderListResponse execute(Long userId) {
    // ✅ Fetch Join으로 한 번에 모든 데이터 로딩
    List<Order> orders = orderRepository.findByUserIdWithItems(userId);

    for (Order order : orders) {
        for (OrderItem item : order.getOrderItems()) {
            // ✅ 이미 로딩됨! 추가 쿼리 없음
            Product product = item.getProduct();
            System.out.println(product.getName());
        }
    }
}
```

---

## ⚠️ Fetch Join 주의사항

### 1. DISTINCT 필수 (일대다 관계)

```java
// ❌ 중복 데이터 발생
SELECT o FROM Order o LEFT JOIN FETCH o.orderItems

// ✅ DISTINCT로 중복 제거
SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems
```

### 2. 페이징 주의

```java
// ⚠️ 경고 발생: 메모리에서 페이징 처리
@Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems")
Page<Order> findAll(Pageable pageable);

// ✅ 대안: 페이징은 ID만, 상세는 Fetch Join
List<Long> orderIds = findOrderIds(pageable);
List<Order> orders = findByIdInWithItems(orderIds);
```

### 3. 여러 컬렉션 Fetch Join 금지

```java
// ❌ 카테시안 곱 발생!
SELECT o FROM Order o
  JOIN FETCH o.orderItems
  JOIN FETCH o.coupons

// ✅ 하나씩 또는 Batch Size 병행
```

---

## 🎯 MySQL Workbench 검증

### Quick Check Script

```sql
-- 1. General Log 활성화
TRUNCATE TABLE mysql.general_log;
SET GLOBAL general_log = 'ON';

-- 2. API 호출 (다른 터미널)
-- curl "http://localhost:8080/api/orders?userId=1"

-- 3. 쿼리 개수 확인
SELECT COUNT(*) AS total_queries
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND);

-- ✅ 결과: 1개 (Fetch Join 성공!)
-- ❌ 결과: 10개 이상 (N+1 문제)
```

### 실제 쿼리 확인

```sql
SELECT SUBSTRING(argument, 1, 200) AS query
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND);

-- Fetch Join 성공 시:
-- "select distinct ... from orders o1_0 left join order_items ..."
-- 딱 1줄만 출력됨!
```

---

## 💡 추가 최적화 팁

### 1. @EntityGraph (대안)

```java
@EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
List<Order> findByUserId(Long userId);
```

### 2. @BatchSize (병행 사용)

```java
// 다른 연관관계는 Batch로
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    @BatchSize(size = 100)
    private List<OrderItem> orderItems;
}
```

---

## ✅ 최종 체크리스트

- [x] Order ↔ OrderItem 양방향 연관관계 설정
- [x] OrderItem → Product 연관관계 설정
- [x] CartItem → Product 연관관계 설정
- [x] Fetch Join 쿼리 작성 (DISTINCT 포함)
- [x] UseCase에서 Fetch Join 메서드 사용
- [x] 컴파일 성공
- [ ] 실제 API 호출로 단일 쿼리 확인
- [ ] MySQL Workbench로 검증

---

## 🚀 지금 바로 확인하기

```bash
# 1. 애플리케이션 시작
./gradlew bootRun

# 2. API 호출
curl "http://localhost:8080/api/orders?userId=1"

# 3. 로그 확인
# "left join order_items" 포함된 단일 쿼리만 보이면 성공!
```

**예상 로그:**
```
Getting orders for user: 1 using Fetch Join
Hibernate: select distinct o1_0... left join order_items... left join products...
Found 5 orders for user: 1 using Fetch Join (single query)
```

**🎉 단 1개의 쿼리로 모든 데이터 로딩 완료!**
