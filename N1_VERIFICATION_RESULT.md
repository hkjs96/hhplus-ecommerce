# N+1 문제 해결 검증 결과

## ✅ 코드 레벨 확인 완료

### 1. Order ↔ OrderItem 양방향 연관관계

**Order.java:**
```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<OrderItem> orderItems = new ArrayList<>();
```

**OrderItem.java:**
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
private Order order;
```

✅ **확인 사항:**
- `@OneToMany(mappedBy = "order")`: OrderItem이 연관관계 주인
- `fetch = FetchType.LAZY`: 지연 로딩 설정
- `cascade = CascadeType.ALL`: 영속성 전이

---

### 2. OrderItem → Product 연관관계

**OrderItem.java:**
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_product"))
private Product product;
```

✅ **확인 사항:**
- `@ManyToOne`: OrderItem 여러 개가 Product 하나 참조
- `fetch = FetchType.LAZY`: 지연 로딩
- `optional = false`: Product는 필수

---

### 3. Batch Fetch Size 설정

**application.yml:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100  # N+1 문제 방지
```

✅ **동작 방식:**
- OrderItem을 로딩할 때 최대 100개씩 묶어서 `IN (?, ?, ...)` 쿼리 실행
- Product를 로딩할 때도 최대 100개씩 묶어서 실행

---

## 🧪 실제 동작 확인 방법

### 방법 1: 애플리케이션 실행 + API 호출 (가장 확실)

```bash
# 1. 애플리케이션 시작
./gradlew bootRun

# 2. 다른 터미널에서 API 호출
curl "http://localhost:8080/api/orders?userId=1" | jq

# 3. 첫 번째 터미널의 로그 확인
# 아래와 같은 패턴이면 성공:
```

**N+1 문제가 해결된 경우 (성공):**
```sql
-- 1. Order 조회
Hibernate:
    select o1_0.id, o1_0.user_id, o1_0.order_number, ...
    from orders o1_0
    where o1_0.user_id=?

-- 2. OrderItem Batch 조회 (IN 절로 한번에!)
Hibernate:
    select oi1_0.order_id, oi1_0.id, oi1_0.product_id, ...
    from order_items oi1_0
    where oi1_0.order_id in (?, ?, ?, ?)  -- 여러 order_id를 한번에

-- 3. Product Batch 조회 (IN 절로 한번에!)
Hibernate:
    select p1_0.id, p1_0.name, p1_0.price, ...
    from products p1_0
    where p1_0.id in (?, ?, ?, ?)  -- 여러 product_id를 한번에
```

**N+1 문제가 있는 경우 (실패):**
```sql
-- 1. Order 조회
SELECT * FROM orders WHERE user_id = ?

-- 2. OrderItem 개별 조회 (주문마다 반복!)
SELECT * FROM order_items WHERE order_id = 1
SELECT * FROM order_items WHERE order_id = 2
SELECT * FROM order_items WHERE order_id = 3
...

-- 3. Product 개별 조회 (OrderItem마다 반복!)
SELECT * FROM products WHERE id = 1
SELECT * FROM products WHERE id = 2
SELECT * FROM products WHERE id = 3
...
```

---

### 방법 2: 테스트 코드 작성

```java
@Test
@Transactional
void verifyN1Solution() {
    // Given
    Long userId = 1L;

    // When: 사용자의 주문 조회
    List<Order> orders = orderRepository.findByUserId(userId);

    // Then: OrderItem 접근 (Batch Fetch 발동)
    for (Order order : orders) {
        List<OrderItem> items = order.getOrderItems();

        // Product 접근 (Batch Fetch 발동)
        for (OrderItem item : items) {
            Product product = item.getProduct();
            System.out.println(product.getName());
        }
    }

    // 콘솔에서 SELECT 쿼리 개수 확인
    // 3개 정도면 성공! (Orders, OrderItems batch, Products batch)
}
```

---

### 방법 3: SQL 카운팅 자동화 (고급)

```yaml
# application.yml에 추가
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true  # 통계 수집

logging:
  level:
    org.hibernate.stat: DEBUG  # 통계 로그 출력
```

실행 후 로그에서 확인:
```
Session Metrics {
    456 nanoseconds spent preparing 3 JDBC statements;  <-- 3개!
    789 nanoseconds spent executing 3 JDBC statements;
}
```

---

## 📊 성능 비교표

| 시나리오 | N+1 있을 때 | 해결 후 |
|---------|------------|---------|
| 사용자 주문 10개 조회 | 1 + 10 = **11 쿼리** | 1 + 1 = **2 쿼리** |
| 주문 상품 정보 포함 | 1 + 10 + 30 = **41 쿼리** | 1 + 1 + 1 = **3 쿼리** |
| 주문 100개 조회 | 1 + 100 = **101 쿼리** | 1 + 1 = **2 쿼리** |

---

## 💡 추가 최적화 옵션

현재 적용된 방식: **LAZY + Batch Size**

### 대안 1: Fetch Join (필요시 UseCase 레벨에서 사용)
```java
@Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**장점:** 한 방 쿼리로 모든 데이터 로딩
**단점:** 페이징 불가, 중복 데이터, 카테시안 곱

### 대안 2: EntityGraph
```java
@EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
List<Order> findByUserId(Long userId);
```

**장점:** 어노테이션 기반으로 간편
**단점:** Fetch Join과 유사한 단점

---

## 🎯 율무 코치님 피드백 충족 여부

### 원본 피드백
> "패치 조인으로 가져온다. 배치사이즈라는 것으로 인접에 관계 있는 엔티티 아이디를 통해 쪼금씩 가져오게 하는 방식도 있다."

### 적용 내용
✅ **Batch Size 방식 채택**
- `default_batch_fetch_size: 100` 설정
- 양방향 연관관계 구성
- LAZY Fetch 전략

### 왜 Batch Size를 선택했나?
1. **범용성**: 모든 연관관계에 자동 적용
2. **페이징 지원**: 페이징 쿼리와 호환
3. **유연성**: UseCase마다 다른 로딩 전략 가능
4. **간결성**: 별도 쿼리 작성 불필요

---

## ✅ 최종 체크리스트

- [x] Order ↔ OrderItem 양방향 연관관계 설정
- [x] OrderItem → Product 연관관계 설정
- [x] CartItem → Product 연관관계 설정
- [x] fetch = FetchType.LAZY 설정
- [x] default_batch_fetch_size: 100 설정
- [x] 하위 호환 메서드 제공 (getProductId(), getOrderId())
- [x] 컴파일 성공
- [ ] 실제 API 호출로 SQL 로그 확인 (사용자가 직접 확인 필요)

---

## 🚀 다음 단계

1. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```

2. **API 호출**
   ```bash
   curl "http://localhost:8080/api/orders?userId=1"
   ```

3. **콘솔 로그 확인**
   - `select ... from orders` 1개
   - `select ... from order_items where order_id in (...)` 1개
   - `select ... from products where id in (...)` 1개
   - **총 3개 쿼리면 성공!** 🎉

4. **문제가 있다면**
   - SQL 로그 캡처해서 공유
   - `@Transactional` 누락 여부 확인
   - Hibernate version 확인
