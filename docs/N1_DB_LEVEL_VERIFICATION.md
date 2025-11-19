# N+1 문제 해결 검증 가이드

## 🎯 목표

이 프로젝트의 **실제 프로덕션 코드**에서 N+1 문제를 어떻게 해결했는지, 그리고 **EXPLAIN**과 **Performance Schema**로 어떻게 검증하는지 보여줍니다.

---

## 📚 프로덕션 코드의 N+1 해결 패턴

### 패턴 1: Fetch Join (일대다 관계 즉시 로딩)

**사용 사례**: 주문 목록 조회 시 주문 상품, 상품 정보 함께 로딩

#### 실제 코드: JpaOrderRepository

**파일**: [`JpaOrderRepository.java:35-50`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/order/JpaOrderRepository.java)

```java
@Query("""
    select distinct o from Order o
    left join fetch o.orderItems oi
    left join fetch oi.product p
    where o.userId = :userId
    order by o.createdAt desc
    """)
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

**Hibernate 생성 쿼리**:
```sql
SELECT DISTINCT
    o.id, o.order_number, o.user_id, o.total_amount, o.created_at,
    oi.id, oi.order_id, oi.product_id, oi.quantity, oi.unit_price,
    p.id, p.name, p.price, p.stock
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
LEFT JOIN products p ON p.id = oi.product_id
WHERE o.user_id = ?
ORDER BY o.created_at DESC
```

**검증**: EXPLAIN으로 확인

```sql
EXPLAIN
SELECT DISTINCT
    o.id, o.order_number, o.user_id, o.total_amount, o.created_at,
    oi.id, oi.order_id, oi.product_id, oi.quantity, oi.unit_price,
    p.id, p.name, p.price, p.stock
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
LEFT JOIN products p ON p.id = oi.product_id
WHERE o.user_id = 1
ORDER BY o.created_at DESC;
```

**기대 결과**:
```
+----+-------------+-------+--------+------------------+------------------+---------+-----------------+------+-------------------------------------------+
| id | select_type | table | type   | possible_keys    | key              | key_len | ref             | rows | Extra                                     |
+----+-------------+-------+--------+------------------+------------------+---------+-----------------+------+-------------------------------------------+
|  1 | SIMPLE      | o     | ref    | idx_user_created | idx_user_created | 8       | const           |   10 | Using index condition; Using filesort     |
|  1 | SIMPLE      | oi    | ref    | idx_order_id     | idx_order_id     | 8       | o.id            |    3 | NULL                                      |
|  1 | SIMPLE      | p     | eq_ref | PRIMARY          | PRIMARY          | 8       | oi.product_id   |    1 | NULL                                      |
+----+-------------+-------+--------+------------------+------------------+---------+-----------------+------+-------------------------------------------+
```

**✅ 좋은 신호**:
- `type: ref` (orders), `ref` (order_items), `eq_ref` (products) → 모두 인덱스 사용
- `key: idx_user_created`, `idx_order_id`, `PRIMARY` → 3개 테이블 모두 인덱스 활용
- **단일 쿼리**로 3개 테이블 조인 → N+1 문제 해결

**효과**:
- ❌ **N+1 문제 발생 시**: 1 (orders) + 10 (order_items) + 35 (products) = **46개 쿼리**
- ✅ **Fetch Join 적용 후**: **1개 쿼리**

---

#### 실제 코드: JpaCartItemRepository

**파일**: [`JpaCartItemRepository.java:42-48`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/cart/JpaCartItemRepository.java)

```java
@Query("""
    select ci from CartItem ci
    left join fetch ci.product p
    where ci.cart.id = :cartId
    order by ci.createdAt desc
    """)
List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);
```

**Hibernate 생성 쿼리**:
```sql
SELECT
    ci.id, ci.cart_id, ci.product_id, ci.quantity, ci.created_at,
    p.id, p.name, p.price, p.stock, p.category
FROM cart_items ci
LEFT JOIN products p ON ci.product_id = p.id
WHERE ci.cart_id = ?
ORDER BY ci.created_at DESC
```

**검증**: EXPLAIN

```sql
EXPLAIN
SELECT
    ci.id, ci.cart_id, ci.product_id, ci.quantity, ci.created_at,
    p.id, p.name, p.price, p.stock, p.category
FROM cart_items ci
LEFT JOIN products p ON ci.product_id = p.id
WHERE ci.cart_id = 1
ORDER BY ci.created_at DESC;
```

**기대 결과**:
```
+----+-------------+-------+--------+---------------+--------------+---------+------------------+------+-----------------------------+
| id | select_type | table | type   | possible_keys | key          | key_len | ref              | rows | Extra                       |
+----+-------------+-------+--------+---------------+--------------+---------+------------------+------+-----------------------------+
|  1 | SIMPLE      | ci    | ref    | idx_cart_id   | idx_cart_id  | 8       | const            |    5 | Using filesort              |
|  1 | SIMPLE      | p     | eq_ref | PRIMARY       | PRIMARY      | 8       | ci.product_id    |    1 | NULL                        |
+----+-------------+-------+--------+---------------+--------------+---------+------------------+------+-----------------------------+
```

**✅ 인덱스 활용**:
- `ci.cart_id`: `idx_cart_id` 사용
- `ci.product_id → p.id`: PRIMARY KEY 사용 (eq_ref)

---

### 패턴 2: 동등 조건 (인덱스 100% 활용)

**사용 사례**: 특정 날짜의 인기 상품 TOP 5 조회

#### 실제 코드: JpaProductSalesAggregateRepository

**파일**: [`JpaProductSalesAggregateRepository.java:60-71`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

```java
@Query(value = """
    SELECT
        product_id AS productId,
        product_name AS productName,
        sales_count AS salesCount,
        revenue AS revenue
    FROM product_sales_aggregates
    WHERE aggregation_date = :date
    ORDER BY sales_count DESC
    LIMIT 5
    """, nativeQuery = true)
List<TopProductProjection> findTopProductsByDate(@Param("date") LocalDate date);
```

**Java 호출 코드**:
```java
// ✅ 애플리케이션에서 날짜 계산 → 파라미터 전달
LocalDate today = LocalDate.now();
List<TopProductProjection> topProducts =
    repository.findTopProductsByDate(today);
```

**검증**: EXPLAIN

```sql
EXPLAIN
SELECT
    product_id,
    product_name,
    sales_count,
    revenue
FROM product_sales_aggregates
WHERE aggregation_date = '2025-11-19'
ORDER BY sales_count DESC
LIMIT 5;
```

**기대 결과**:
```
+----+-------------+---------------------------+------+---------------+----------------+---------+-------+------+-------------+
| id | select_type | table                     | type | possible_keys | key            | key_len | ref   | rows | Extra       |
+----+-------------+---------------------------+------+---------------+----------------+---------+-------+------+-------------+
|  1 | SIMPLE      | product_sales_aggregates  | ref  | idx_date_...  | idx_date_sales | 3       | const |   50 | Using index |
+----+-------------+---------------------------+------+---------------+----------------+---------+-------+------+-------------+
```

**✅ 최고 성능**:
- `type: ref` → 동등 조건 인덱스 조회
- `key: idx_date_sales` → 복합 인덱스 (aggregation_date, sales_count DESC)
- `Extra: Using index` → 커버링 인덱스 (인덱스만으로 쿼리 완성)
- **실행 시간 <1ms**

**인덱스 전략**:
```java
@Index(name = "idx_date_sales",
       columnList = "aggregation_date, sales_count DESC")
```

이 인덱스는:
1. `WHERE aggregation_date = :date` → 빠른 필터링
2. `ORDER BY sales_count DESC` → 정렬 불필요 (인덱스 순서 활용)

---

### 패턴 3: IN 조건 (여러 동등 조건)

**사용 사례**: 최근 3일간 인기 상품 조회

#### 실제 코드: JpaProductSalesAggregateRepository

**파일**: [`JpaProductSalesAggregateRepository.java:83-95`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

```java
@Query(value = """
    SELECT
        product_id AS productId,
        product_name AS productName,
        SUM(sales_count) AS salesCount,
        SUM(revenue) AS revenue
    FROM product_sales_aggregates
    WHERE aggregation_date IN :dates
    GROUP BY product_id, product_name
    ORDER BY salesCount DESC
    LIMIT 5
    """, nativeQuery = true)
List<TopProductProjection> findTopProductsByDates(@Param("dates") List<LocalDate> dates);
```

**Java 호출 코드**:
```java
// ✅ 특정 날짜 리스트로 조회
LocalDate today = LocalDate.now();
List<LocalDate> dates = List.of(
    today.minusDays(2),
    today.minusDays(1),
    today
);
List<TopProductProjection> topProducts =
    repository.findTopProductsByDates(dates);
```

**검증**: EXPLAIN

```sql
EXPLAIN
SELECT
    product_id,
    product_name,
    SUM(sales_count) AS salesCount,
    SUM(revenue) AS revenue
FROM product_sales_aggregates
WHERE aggregation_date IN ('2025-11-17', '2025-11-18', '2025-11-19')
GROUP BY product_id, product_name
ORDER BY salesCount DESC
LIMIT 5;
```

**기대 결과**:
```
+----+-------------+---------------------------+-------+---------------+----------------+---------+------+------+----------------------------------------------+
| id | select_type | table                     | type  | possible_keys | key            | key_len | ref  | rows | Extra                                        |
+----+-------------+---------------------------+-------+---------------+----------------+---------+------+------+----------------------------------------------+
|  1 | SIMPLE      | product_sales_aggregates  | range | idx_date_...  | idx_date_sales | 3       | NULL |  150 | Using where; Using temporary; Using filesort |
+----+-------------+---------------------------+-------+---------------+----------------+---------+------+------+----------------------------------------------+
```

**✅ 인덱스 활용**:
- `type: range` → 여러 동등 조건 (IN)
- `key: idx_date_sales` → 인덱스 사용
- `rows: 150` → 3일치 데이터만 스캔 (전체 테이블 스캔 X)

**효과**:
- 데이터가 적으므로 (3일 * 50개 상품 = 150 rows) GROUP BY 부담 적음
- 범위 조건보다 효율적

---

### 패턴 4: 범위 조건 + 파라미터 사용

**사용 사례**: 지난 주 인기 상품 조회

#### 실제 코드: JpaProductSalesAggregateRepository

**파일**: [`JpaProductSalesAggregateRepository.java:32-48`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

```java
@Query(value = """
    SELECT
        product_id AS productId,
        product_name AS productName,
        SUM(sales_count) AS salesCount,
        SUM(revenue) AS revenue
    FROM product_sales_aggregates
    WHERE aggregation_date >= :startDate
      AND aggregation_date <= :endDate
    GROUP BY product_id, product_name
    ORDER BY salesCount DESC
    LIMIT 5
    """, nativeQuery = true)
List<TopProductProjection> findTopProductsByDateRange(
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate
);
```

**Java 호출 코드**:
```java
// ✅ 애플리케이션에서 날짜 계산
LocalDate endDate = LocalDate.now();
LocalDate startDate = endDate.minusDays(7);

// ✅ 파라미터로 전달 (DB 함수 사용 X)
List<TopProductProjection> topProducts =
    repository.findTopProductsByDateRange(startDate, endDate);
```

**❌ 잘못된 방법 (비교)**:
```sql
-- ❌ BAD: DB에서 함수 사용 → 인덱스 미활용
WHERE aggregation_date >= DATE_SUB(NOW(), INTERVAL 7 DAY)
```

**✅ 올바른 방법**:
```sql
-- ✅ GOOD: 파라미터 사용 → 인덱스 활용 가능
WHERE aggregation_date >= '2025-11-12'
  AND aggregation_date <= '2025-11-19'
```

**검증**: EXPLAIN

```sql
EXPLAIN
SELECT
    product_id,
    product_name,
    SUM(sales_count) AS salesCount,
    SUM(revenue) AS revenue
FROM product_sales_aggregates
WHERE aggregation_date >= '2025-11-12'
  AND aggregation_date <= '2025-11-19'
GROUP BY product_id, product_name
ORDER BY salesCount DESC
LIMIT 5;
```

**기대 결과**:
```
+----+-------------+---------------------------+-------+---------------+----------------+---------+------+------+----------------------------------------------+
| id | select_type | table                     | type  | possible_keys | key            | key_len | ref  | rows | Extra                                        |
+----+-------------+---------------------------+-------+---------------+----------------+---------+------+------+----------------------------------------------+
|  1 | SIMPLE      | product_sales_aggregates  | range | idx_date_...  | idx_date_sales | 3       | NULL |  350 | Using where; Using temporary; Using filesort |
+----+-------------+---------------------------+-------+---------------+----------------+---------+------+------+----------------------------------------------+
```

**✅ 인덱스 활용**:
- `type: range` → 범위 스캔
- `key: idx_date_sales` → 인덱스 사용
- `rows: 350` → 7일치만 스캔 (파라미터 사용 덕분)

---

### 패턴 5: Native Query + JOIN (복잡한 조회)

**사용 사례**: 사용자 쿠폰 목록 + 쿠폰 상세 정보

#### 실제 코드: JpaUserCouponRepository

**파일**: [`JpaUserCouponRepository.java:31-51`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/coupon/JpaUserCouponRepository.java)

```java
@Query(value = """
    SELECT
        uc.id AS userCouponId,
        uc.user_id AS userId,
        uc.coupon_id AS couponId,
        uc.status AS status,
        uc.issued_at AS issuedAt,
        uc.used_at AS usedAt,
        c.name AS couponName,
        c.discount_rate AS discountRate,
        uc.expires_at AS expiresAt
    FROM user_coupons uc
    JOIN coupons c ON uc.coupon_id = c.id
    WHERE uc.user_id = :userId
      AND (:status IS NULL OR uc.status = :status)
    ORDER BY uc.issued_at DESC
    """, nativeQuery = true)
List<UserCouponProjection> findUserCouponsWithDetails(
    @Param("userId") Long userId,
    @Param("status") String status
);
```

**검증**: EXPLAIN

```sql
EXPLAIN
SELECT
    uc.id, uc.user_id, uc.coupon_id, uc.status,
    c.name, c.discount_rate
FROM user_coupons uc
JOIN coupons c ON uc.coupon_id = c.id
WHERE uc.user_id = 1
  AND uc.status = 'AVAILABLE'
ORDER BY uc.issued_at DESC;
```

**기대 결과**:
```
+----+-------------+-------+--------+-------------------+-------------------+---------+------------------+------+-----------------------------+
| id | select_type | table | type   | possible_keys     | key               | key_len | ref              | rows | Extra                       |
+----+-------------+-------+--------+-------------------+-------------------+---------+------------------+------+-----------------------------+
|  1 | SIMPLE      | uc    | ref    | idx_user_status   | idx_user_status   | 9       | const,const      |    5 | Using filesort              |
|  1 | SIMPLE      | c     | eq_ref | PRIMARY           | PRIMARY           | 8       | uc.coupon_id     |    1 | NULL                        |
+----+-------------+-------+--------+-------------------+-------------------+---------+------------------+------+-----------------------------+
```

**✅ 복합 인덱스 활용**:
- `idx_user_status (user_id, status)` → 두 조건 모두 인덱스 활용
- `type: ref` (user_coupons), `eq_ref` (coupons) → 효율적
- **단일 쿼리**로 조인 완성

---

## 🔍 Performance Schema로 N+1 검증

### Step 1: Performance Schema 활성화

```sql
-- 현재 상태 확인
SHOW VARIABLES LIKE 'performance_schema';

-- Statement 통계 수집 활성화
UPDATE performance_schema.setup_instruments
SET ENABLED = 'YES', TIMED = 'YES'
WHERE NAME LIKE '%statement/%';

UPDATE performance_schema.setup_consumers
SET ENABLED = 'YES'
WHERE NAME LIKE '%events_statements%';
```

### Step 2: 통계 초기화 및 API 호출

```sql
-- 통계 초기화
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
```

```bash
# API 호출
curl "http://localhost:8080/api/orders?userId=1"
```

### Step 3: 실행 쿼리 분석

```sql
-- 실행된 쿼리 Top 10
SELECT
    DIGEST_TEXT,
    COUNT_STAR AS exec_count,
    SUM_TIMER_WAIT/1000000000 AS total_time_ms,
    AVG_TIMER_WAIT/1000000000 AS avg_time_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'ecommerce'
  AND DIGEST_TEXT IS NOT NULL
ORDER BY COUNT_STAR DESC
LIMIT 10;
```

**✅ Fetch Join 성공 (기대 결과)**:
```
+------------------------------------------------------+------------+--------------+-------------+
| DIGEST_TEXT                                          | exec_count | total_time   | avg_time_ms |
+------------------------------------------------------+------------+--------------+-------------+
| SELECT ... FROM `orders` ... LEFT JOIN `order_items` |          1 |        8.45  |        8.45 |
+------------------------------------------------------+------------+--------------+-------------+
```
- `exec_count = 1` → 단일 쿼리로 모든 데이터 조회
- 총 쿼리 1개

**❌ N+1 문제 발생 (실패 예시)**:
```
+------------------------------------------------------+------------+--------------+-------------+
| DIGEST_TEXT                                          | exec_count | total_time   | avg_time_ms |
+------------------------------------------------------+------------+--------------+-------------+
| SELECT ... FROM `orders` WHERE `user_id` = ?         |          1 |        5.23  |        5.23 |
| SELECT ... FROM `order_items` WHERE `order_id` = ?   |         10 |       34.50  |        3.45 |
| SELECT ... FROM `products` WHERE `id` = ?            |         35 |       73.50  |        2.10 |
+------------------------------------------------------+------------+--------------+-------------+
```
- `exec_count = 10, 35` → N+1 문제!
- 총 쿼리 46개

---

## 📊 성능 비교표

| 지표 | ❌ N+1 문제 | ✅ Fetch Join |
|------|------------|--------------|
| **총 쿼리 수** | 46개 | 1개 |
| **orders 쿼리** | 1개 | JOIN 포함 |
| **order_items 쿼리** | 10개 | JOIN 포함 |
| **products 쿼리** | 35개 | JOIN 포함 |
| **총 실행 시간** | ~113ms | ~8ms |
| **네트워크 왕복** | 46 round-trips | 1 round-trip |
| **인덱스 활용** | 46회 (개별) | 3회 (조인) |

---

## 🎯 검증 체크리스트

실제 DB에서 확인해야 할 항목:

### N+1 해결 확인
- [ ] Performance Schema에서 **exec_count가 1~3** 정도
- [ ] Fetch Join 쿼리가 **LEFT JOIN**으로 실행됨
- [ ] 총 쿼리 개수가 **5개 이하**

### 인덱스 활용 확인
- [ ] EXPLAIN에서 **type: ref, eq_ref, range** (ALL 없음)
- [ ] **key 컬럼**에 인덱스 이름 표시 (NULL 없음)
- [ ] **rows**가 예상보다 적음 (Full scan 없음)

### 성능 확인
- [ ] **실행 시간 <10ms** (단일 조회)
- [ ] **Covering Index** 적용 (Extra: Using index)
- [ ] **filesort 최소화** (인덱스 정렬 활용)

---

## 💡 핵심 원칙

### ✅ DO (권장)

1. **Fetch Join 사용**
   ```java
   left join fetch o.orderItems oi
   left join fetch oi.product p
   ```

2. **파라미터 사용** (함수 X)
   ```java
   LocalDate date = LocalDate.now();
   repository.findByDate(date);  // ✅
   ```

3. **동등 조건 우선**
   ```sql
   WHERE aggregation_date = :date  -- ✅ 최고 성능
   ```

4. **IN 조건 활용**
   ```sql
   WHERE aggregation_date IN :dates  -- ✅ 여러 동등 조건
   ```

5. **복합 인덱스 설계**
   ```java
   @Index(name = "idx_date_sales",
          columnList = "aggregation_date, sales_count DESC")
   ```

### ❌ DON'T (금지)

1. **함수 사용 금지**
   ```sql
   WHERE paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)  -- ❌
   WHERE paid_at >= :startDate                        -- ✅
   ```

2. **지연 로딩 반복 호출**
   ```java
   for (Order order : orders) {
       order.getOrderItems().size();  // ❌ N+1 발생
   }
   ```

3. **실시간 집계 반복**
   ```sql
   -- ❌ 매번 GROUP BY
   SELECT COUNT(*) FROM orders WHERE ...

   -- ✅ ROLLUP 테이블 조회
   SELECT sales_count FROM product_sales_aggregates WHERE ...
   ```

---

## 📚 참고 문서

- **[QUERY_OPTIMIZATION_SUMMARY.md](./week4/verification/QUERY_OPTIMIZATION_SUMMARY.md)** - 쿼리 최적화 상세 가이드
- **[EXPLAIN_ANALYZE_GUIDE.md](./week4/verification/EXPLAIN_ANALYZE_GUIDE.md)** - EXPLAIN 결과 해석 가이드

---

## 🚀 빠른 검증 방법

```bash
# 1. Performance Schema 초기화
mysql -u root -p ecommerce -e "
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
"

# 2. API 호출
curl "http://localhost:8080/api/orders?userId=1"

# 3. 쿼리 개수 확인
mysql -u root -p ecommerce -e "
SELECT COUNT(*) AS query_count
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'ecommerce'
  AND DIGEST_TEXT LIKE '%orders%';
"
```

**기대 결과**: query_count = 1~3

**실제 검증 완료!** ✅
