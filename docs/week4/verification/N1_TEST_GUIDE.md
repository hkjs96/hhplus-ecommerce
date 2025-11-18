# N+1 문제 해결 확인 가이드

## ✅ 적용된 내용

### 1. 양방향 연관관계 설정
```java
// Order.java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
private List<OrderItem> orderItems = new ArrayList<>();

// OrderItem.java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "order_id")
private Order order;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "product_id")
private Product product;
```

### 2. Batch Fetch Size 설정
```yaml
# application.yml에 이미 설정됨
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

## 🔍 확인 방법

### 방법 1: 애플리케이션 실행 후 API 호출

1. **애플리케이션 시작**
   ```bash
   ./gradlew bootRun
   ```

2. **API 호출 (주문 목록 조회)**
   ```bash
   curl "http://localhost:8080/api/orders?userId=1"
   ```

3. **콘솔 로그에서 SQL 확인**
   ```
   # N+1 문제가 있으면:
   Hibernate: select o1_0.id, ... from orders o1_0 where o1_0.user_id=?
   Hibernate: select oi1_0.order_id, ... from order_items oi1_0 where oi1_0.order_id=?  -- 주문마다 반복!
   Hibernate: select oi1_0.order_id, ... from order_items oi1_0 where oi1_0.order_id=?
   ... (주문 개수만큼 반복)

   # Batch Fetch가 동작하면:
   Hibernate: select o1_0.id, ... from orders o1_0 where o1_0.user_id=?
   Hibernate: select oi1_0.order_id, ... from order_items oi1_0 where oi1_0.order_id in (?, ?, ?, ...)  -- IN 절로 한번에!
   Hibernate: select p1_0.id, ... from products p1_0 where p1_0.id in (?, ?, ?, ...)  -- Product도 한번에!
   ```

### 방법 2: 테스트 코드로 확인

아래 코드를 테스트 파일에 추가하고 실행:

```java
@Test
@Transactional
void verifyBatchFetching() {
    // 1. Order 조회
    List<Order> orders = orderRepository.findByUserId(1L);

    // 2. OrderItem 접근 (이때 Batch Fetch 발동)
    for (Order order : orders) {
        System.out.println("Order: " + order.getId());
        List<OrderItem> items = order.getOrderItems();

        // 3. Product 접근 (이때도 Batch Fetch 발동)
        for (OrderItem item : items) {
            System.out.println("  - Product: " + item.getProduct().getName());
        }
    }

    // 콘솔 로그에서 SELECT 쿼리가 3번 정도만 나오면 성공!
    // (1: Orders, 1: OrderItems batch, 1: Products batch)
}
```

### 방법 3: Hibernate Statistics 활성화

```yaml
# application.yml에 추가
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
logging:
  level:
    org.hibernate.stat: DEBUG
```

그러면 쿼리 통계가 자동으로 출력됩니다:
```
Session Metrics {
    123 nanoseconds spent acquiring 1 JDBC connections;
    0 nanoseconds spent releasing 0 JDBC connections;
    456 nanoseconds spent preparing 3 JDBC statements;  <-- 쿼리 개수
    789 nanoseconds spent executing 3 JDBC statements;  <-- 3개만 실행됨!
    ...
}
```

## 📊 성능 비교

| 상황 | N+1 있을 때 | Batch Fetch 적용 후 |
|------|-------------|---------------------|
| 주문 10개 조회 | 1 + 10 = 11개 쿼리 | 1 + 1 = 2개 쿼리 |
| 주문 100개 조회 | 1 + 100 = 101개 쿼리 | 1 + 1 = 2개 쿼리 |
| 상품 정보 포함 | 1 + 10 + 30 = 41개 쿼리 | 1 + 1 + 1 = 3개 쿼리 |

## 🎯 확인 포인트

✅ **성공 기준**
- [ ] Order 조회 쿼리 1개
- [ ] OrderItem 조회 쿼리 1개 (IN 절 사용)
- [ ] Product 조회 쿼리 1개 (IN 절 사용)
- [ ] 총 3개 이하의 SELECT 쿼리

❌ **문제 있음**
- [ ] Order 개수만큼 OrderItem 쿼리 발생 (10개 주문 = 10개 쿼리)
- [ ] OrderItem 개수만큼 Product 쿼리 발생
- [ ] SELECT 쿼리가 10개 이상

## 💡 추가 최적화 방법

현재 `LAZY + Batch Size` 방식 외에도:

### Fetch Join 사용 (필요시)
```java
@Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

### EntityGraph 사용
```java
@EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
List<Order> findByUserId(Long userId);
```

## 🔧 디버깅 팁

만약 Batch Fetch가 동작하지 않는다면:

1. **@Transactional 확인**: 트랜잭션 안에서 실행되어야 Lazy Loading 작동
2. **application.yml 확인**: `default_batch_fetch_size` 설정 확인
3. **Hibernate 버전 확인**: Spring Boot 3.x는 Hibernate 6.x 사용
4. **SQL 로그 레벨**: `org.hibernate.SQL: DEBUG` 설정

## 📚 참고 자료

- 율무 코치님 피드백: "패치 조인으로 가져오거나, 배치 사이즈로 인접한 엔티티 ID를 통해 조금씩 가져오게 하는 방식"
- Hibernate Batch Fetching: https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#fetching-batch
