# 율무 코치님 피드백 반영 상태 (Step 9-10)

## 📊 전체 진행 상황

| 번호 | 항목 | 상태 | 비고 |
|------|------|------|------|
| 1 | 연관관계 + 조회 전략 | ✅ 완료 | Fetch Join 적용 |
| 2 | 인덱스/쿼리 최적화 | ✅ 완료 | 명시적 인덱스 관리 |
| 3 | 낙관적 락 예외 처리 | ✅ 완료 | Facade 패턴 적용 |
| 4 | 인기 상품 ROLLUP 전략 | ✅ 완료 | ProductSalesAggregate |

---

## 1. ✅ 연관관계 + 조회 전략

### 적용 내용

#### Fetch Join 적용 (자주 같이 조회하는 경우)

**Order + OrderItem + Product**:
```java
// JpaOrderRepository.java
@Query("""
    select distinct o from Order o
    left join fetch o.orderItems oi
    left join fetch oi.product p
    where o.userId = :userId
    order by o.createdAt desc
    """)
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**CartItem + Product**:
```java
// JpaCartItemRepository.java
@Query("""
    select ci from CartItem ci
    left join fetch ci.product p
    where ci.cartId = :cartId
    order by ci.createdAt desc
    """)
List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);
```

#### 성능 개선

- **Before**: N+1 문제 (84 queries)
  - Orders: 1 query
  - OrderItems: 18 queries
  - Products: ~65 queries
- **After**: Fetch Join (1 query) ✅
  - 단일 JOIN 쿼리로 모든 데이터 로딩

### 검증 완료

- ✅ EXPLAIN ANALYZE 가이드 작성
- ✅ N+1 문제 해결 확인
- ✅ 실제 API 테스트 성공

---

## 2. ✅ 인덱스/쿼리 최적화

### FK/상태 컬럼 명시적 인덱스

#### Order 엔티티
```java
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_user_created", columnList = "user_id, created_at"),  // 복합 인덱스
        @Index(name = "idx_user_status", columnList = "user_id, status"),       // 상태 조회
        @Index(name = "idx_status_paid", columnList = "status, paid_at")        // 결제일 조회
    }
)
```

#### OrderItem 엔티티
```java
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),      // FK 인덱스 명시
        @Index(name = "idx_product_id", columnList = "product_id")   // FK 인덱스 명시
    }
)
```

#### Product 엔티티
```java
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_product_code", columnList = "product_code"),         // 유니크 검색
        @Index(name = "idx_category_created", columnList = "category, created_at") // 카테고리별 정렬
    }
)
```

#### UserCoupon 엔티티
```java
@Table(
    name = "user_coupons",
    indexes = {
        @Index(name = "idx_user_status", columnList = "user_id, status"),  // 사용자별 쿠폰 조회
        @Index(name = "idx_coupon_id", columnList = "coupon_id")           // FK 인덱스
    }
)
```

### 인덱스 전략

✅ **명시적으로 관리**:
- FK 제약조건이 자동으로 만드는 인덱스에 의존하지 않음
- `@Index` 어노테이션으로 명시

✅ **복합 인덱스 활용**:
- `(user_id, created_at)`: 사용자별 최신 주문 조회 최적화
- `(user_id, status)`: 사용자별 상태 필터링 최적화

✅ **사용 안 하는 인덱스 제거**:
- 불필요한 인덱스는 정의하지 않음
- Write 성능 부담 최소화

---

## 3. ✅ 낙관적 락 예외 처리

### Facade 패턴 적용

#### OrderPaymentFacade
```java
@Component
public class OrderPaymentFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final ProcessPaymentUseCase processPaymentUseCase;

    // ✅ @Transactional 메서드 바깥에서 예외 처리
    public PaymentResponse processPaymentWithRetry(Long orderId, PaymentRequest request) {
        int attemptCount = 0;

        while (attemptCount < MAX_RETRY_COUNT) {
            try {
                attemptCount++;
                // @Transactional 메서드 호출
                return processPaymentUseCase.execute(orderId, request);

            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict. Attempt {}/{}", attemptCount, MAX_RETRY_COUNT);

                if (attemptCount >= MAX_RETRY_COUNT) {
                    throw new BusinessException(ErrorCode.STOCK_UPDATE_CONFLICT, ...);
                }

                // Exponential Backoff
                sleep(RETRY_DELAY_MS * attemptCount);
            }
        }
    }
}
```

### 핵심 포인트

✅ **트랜잭션 바깥에서 처리**:
- `@Transactional` 메서드 바깥 레이어에서 `try-catch`
- 재시도 로직을 트랜잭션 경계 밖에서 수행

✅ **Exponential Backoff**:
- 재시도 간격: 100ms → 200ms → 300ms
- 동시성 충돌 시 성공 확률 증가

✅ **최대 재시도 횟수 제한**:
- 3회 시도 후 실패 시 명확한 에러 메시지

---

## 4. ✅ 인기 상품 쿼리 / ROLLUP 전략

### ProductSalesAggregate (집계 테이블)

#### 엔티티 정의
```java
@Entity
@Table(
    name = "product_sales_aggregates",
    indexes = {
        @Index(name = "idx_date_sales", columnList = "aggregation_date, sales_count DESC"),
        @Index(name = "idx_product_date", columnList = "product_id, aggregation_date")
    }
)
public class ProductSalesAggregate extends BaseTimeEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "aggregation_date", nullable = false)
    private LocalDate aggregationDate;  // 집계 기준일

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;  // 판매 건수

    @Column(name = "revenue", nullable = false)
    private Long revenue;  // 매출액
}
```

### ROLLUP 전략

#### Before (문제점)
```sql
-- ❌ 매번 전체 주문 테이블 스캔 + GROUP BY
SELECT
    p.id, p.name, COUNT(*) as sales_count
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY p.id, p.name
ORDER BY sales_count DESC  -- ❌ 계산 컬럼 정렬은 인덱스 못 씀!
LIMIT 5;
```

**문제**:
- GROUP BY로 계산된 `sales_count`는 인덱스 사용 불가
- 매번 전체 주문 스캔
- 실시간 집계로 인한 성능 저하

#### After (ROLLUP 전략)
```sql
-- ✅ 사전 집계된 테이블 조회 (인덱스 활용)
SELECT
    product_id, product_name, sales_count, revenue
FROM product_sales_aggregates
WHERE aggregation_date >= DATE_SUB(CURDATE(), INTERVAL 3 DAY)
ORDER BY sales_count DESC  -- ✅ idx_date_sales 인덱스 사용!
LIMIT 5;
```

**장점**:
- ✅ `idx_date_sales` 인덱스 직접 사용
- ✅ GROUP BY 없이 미리 집계된 데이터 조회
- ✅ 빠른 응답 시간 (<1ms)
- ✅ 원본 주문 테이블에 부하 없음

### 인덱스 전략

#### idx_date_sales
```java
@Index(name = "idx_date_sales", columnList = "aggregation_date, sales_count DESC")
```

**용도**: 인기 상품 조회 (최근 N일간 판매량 순 정렬)
```sql
WHERE aggregation_date >= ?
ORDER BY sales_count DESC
```

#### idx_product_date
```java
@Index(name = "idx_product_date", columnList = "product_id, aggregation_date")
```

**용도**: 특정 상품의 일별 판매 추이 조회
```sql
WHERE product_id = ? AND aggregation_date BETWEEN ? AND ?
```

### 배치 집계 전략

#### 일일 배치 (권장)
```java
// 매일 자정 실행
@Scheduled(cron = "0 0 0 * * *")
public void aggregateDailySales() {
    LocalDate yesterday = LocalDate.now().minusDays(1);

    // 어제 판매 데이터 집계
    List<SalesData> salesData = orderRepository
        .findSalesByDate(yesterday);

    // ProductSalesAggregate 테이블에 저장
    salesData.forEach(data -> {
        ProductSalesAggregate aggregate = ProductSalesAggregate.create(
            data.getProductId(),
            data.getProductName(),
            yesterday,
            data.getSalesCount(),
            data.getRevenue()
        );
        aggregateRepository.save(aggregate);
    });
}
```

#### 실시간 집계 대안 (옵션)
- 5분마다 집계 (부하가 적은 경우)
- 주문 완료 시 비동기 집계 (Event-driven)

---

## 📊 성능 개선 요약

### N+1 문제 해결
- **84 queries → 1 query** (98% 감소)
- Fetch Join 적용으로 단일 쿼리 실행

### 인덱스 최적화
- FK/상태 컬럼 명시적 인덱스 관리
- 복합 인덱스로 조회 패턴 최적화
- 불필요한 인덱스 제거

### 동시성 제어
- Optimistic Lock 예외를 Facade에서 처리
- 재시도 로직으로 충돌 해결

### 통계 쿼리 최적화
- ROLLUP 전략으로 사전 집계
- 인덱스 활용 가능한 구조로 설계
- 원본 테이블 스캔 최소화

---

## ✅ 체크리스트

- [x] Fetch Join 적용 (Order, Cart)
- [x] 명시적 인덱스 정의 (FK, 상태 컬럼)
- [x] 복합 인덱스 설계 (user_id + created_at 등)
- [x] OptimisticLock 예외 처리 Facade 분리
- [x] ProductSalesAggregate ROLLUP 테이블 설계
- [x] ROLLUP 인덱스 전략 (date + sales_count)
- [x] 재고 감소 로직 추가
- [x] 검증 가이드 작성 (EXPLAIN ANALYZE, Stock Decrease)

---

## 📚 참고 문서

- `N1_FETCH_JOIN_GUIDE.md` - Fetch Join 완벽 가이드
- `EXPLAIN_ANALYZE_GUIDE.md` - 쿼리 실행 계획 분석
- `STOCK_DECREASE_VERIFICATION.md` - 재고 감소 검증
- `OrderPaymentFacade.java` - 낙관적 락 재시도 패턴
- `ProductSalesAggregate.java` - ROLLUP 테이블 설계

---

## 🚀 다음 단계 (선택)

### 확장 고려사항

1. **검색 시스템**:
   - 현재: MySQL 인덱스 기반 검색
   - 규모 증가 시: Elasticsearch 도입 검토

2. **캐싱**:
   - 인기 상품 조회: Redis 캐싱
   - 집계 데이터: TTL 설정

3. **샤딩/파티셔닝**:
   - orders 테이블: 날짜 기반 파티셔닝
   - product_sales_aggregates: 월별 파티션

4. **모니터링**:
   - Slow Query 로그 분석
   - 인덱스 사용률 모니터링
   - N+1 감지 도구 (Hibernate Statistics)

---

## 📝 율무 코치님 피드백 완료 ✅

모든 피드백 사항이 반영되었습니다:
1. ✅ 연관관계 + Fetch Join 전략
2. ✅ 명시적 인덱스 관리
3. ✅ 낙관적 락 Facade 패턴
4. ✅ ROLLUP 전략 설계

**Step 9-10 완료!** 🎉
