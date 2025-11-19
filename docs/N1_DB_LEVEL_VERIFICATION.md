# N+1 문제 DB 레벨에서 확인하기

## 🎯 목표
MySQL Workbench, EXPLAIN, Performance Schema를 사용해서 N+1 문제 해결을 객관적으로 검증

## ⚠️ 중요: 모니터링 쿼리 vs 프로덕션 쿼리

이 문서의 쿼리는 **N+1 문제 검증 및 모니터링용**입니다. 실제 프로덕션 코드에서는 **인덱스 최적화 원칙**을 따라야 합니다.

### 모니터링 쿼리 (이 문서)
- 목적: 문제 진단, 디버깅, 검증
- 특징: 유연성 우선 (LIKE, 함수 사용)
- 사용: DBA, 개발자가 수동 실행

### 프로덕션 쿼리 (실제 코드)
- 목적: 비즈니스 로직 처리
- 특징: 성능 우선 (인덱스 활용)
- 사용: 애플리케이션 자동 실행
- 예시: [JpaProductSalesAggregateRepository.java](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

**💡 자세한 내용은 문서 하단의 [프로덕션 코드 패턴](#-프로덕션-코드에서의-올바른-패턴) 섹션을 참조하세요.**

---

## 방법 1: MySQL Workbench - Query Stats

### Step 1: General Log 활성화

MySQL Workbench에서 실행:

```sql
-- General Log 활성화 (모든 쿼리 기록)
SET GLOBAL general_log = 'ON';
SET GLOBAL log_output = 'TABLE';

-- 기존 로그 초기화
TRUNCATE TABLE mysql.general_log;
```

### Step 2: 애플리케이션에서 API 호출

```bash
# 터미널에서
curl "http://localhost:8080/api/orders?userId=1"
```

### Step 3: 실행된 쿼리 확인

```sql
-- ⚠️ 모니터링 쿼리 (검증 전용)
-- 주의: 이 쿼리는 N+1 문제 검증용입니다.
-- 프로덕션 코드에서는 함수/LIKE 사용 금지!

-- 최근 실행된 SELECT 쿼리 보기
SELECT
    event_time,
    argument,
    SUBSTRING(argument, 1, 100) AS query_preview
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)  -- ⚠️ 함수 사용 (모니터링용)
ORDER BY event_time DESC;
```

**✅ 성공 (Batch Fetch):**
```
3 rows returned
- SELECT ... FROM orders WHERE user_id = ?
- SELECT ... FROM order_items WHERE order_id IN (?, ?, ?, ?)
- SELECT ... FROM products WHERE id IN (?, ?, ?, ?)
```

**❌ 실패 (N+1):**
```
20+ rows returned
- SELECT ... FROM orders WHERE user_id = ?
- SELECT ... FROM order_items WHERE order_id = 1
- SELECT ... FROM order_items WHERE order_id = 2
- SELECT ... FROM order_items WHERE order_id = 3
- ...
```

### Step 4: 쿼리 개수 집계

```sql
-- ⚠️ 모니터링 쿼리 (검증 전용)
-- 주의: LIKE '%text%'는 인덱스 미활용 → 모니터링 전용
-- 프로덕션 코드: docs 하단 "올바른 패턴" 참조

-- SELECT 쿼리 패턴별 개수 확인
SELECT
    CASE
        WHEN argument LIKE '%FROM orders%' THEN 'orders'          -- ⚠️ 중간 매칭 (모니터링용)
        WHEN argument LIKE '%FROM order_items%' THEN 'order_items'
        WHEN argument LIKE '%FROM products%' THEN 'products'
        ELSE 'other'
    END AS query_type,
    COUNT(*) AS query_count,
    CASE
        WHEN argument LIKE '%IN (%' THEN 'Batch (IN clause)'      -- ⚠️ 중간 매칭 (모니터링용)
        ELSE 'Individual'
    END AS fetch_type
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)            -- ⚠️ 함수 사용 (모니터링용)
GROUP BY query_type, fetch_type
ORDER BY query_count DESC;
```

**예상 결과 (성공):**
```
+-------------+-------------+-------------------+
| query_type  | query_count | fetch_type        |
+-------------+-------------+-------------------+
| orders      |           1 | Individual        |
| order_items |           1 | Batch (IN clause) |
| products    |           1 | Batch (IN clause) |
+-------------+-------------+-------------------+
```

---

## 방법 2: EXPLAIN으로 쿼리 플랜 분석

### Step 1: 실제 실행되는 쿼리 복사

General Log에서 실제 쿼리를 복사:

```sql
-- 예: OrderItem Batch 조회 쿼리
SELECT oi.*
FROM order_items oi
WHERE oi.order_id IN (1, 2, 3, 4, 5);
```

### Step 2: EXPLAIN 실행

```sql
EXPLAIN
SELECT oi.*
FROM order_items oi
WHERE oi.order_id IN (1, 2, 3, 4, 5);
```

**결과 분석:**
```
+----+-------------+-------+-------+---------------+--------------+---------+------+------+-------------+
| id | select_type | table | type  | possible_keys | key          | key_len | ref  | rows | Extra       |
+----+-------------+-------+-------+---------------+--------------+---------+------+------+-------------+
|  1 | SIMPLE      | oi    | range | idx_order_id  | idx_order_id | 8       | NULL |   15 | Using where |
+----+-------------+-------+-------+---------------+--------------+---------+------+------+-------------+
```

**✅ 좋은 신호:**
- `type: range` (인덱스 범위 스캔)
- `key: idx_order_id` (인덱스 사용)
- `rows: 15` (적은 row 스캔)

**❌ 나쁜 신호:**
- `type: ALL` (전체 테이블 스캔)
- `key: NULL` (인덱스 미사용)
- `rows: 10000+` (많은 row 스캔)

---

## 방법 3: Performance Schema로 실시간 모니터링

### Step 1: Performance Schema 활성화

```sql
-- Performance Schema 상태 확인
SHOW VARIABLES LIKE 'performance_schema';

-- Statement 통계 활성화
UPDATE performance_schema.setup_instruments
SET ENABLED = 'YES', TIMED = 'YES'
WHERE NAME LIKE '%statement/%';

UPDATE performance_schema.setup_consumers
SET ENABLED = 'YES'
WHERE NAME LIKE '%events_statements%';
```

### Step 2: 통계 초기화

```sql
-- 기존 통계 초기화
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
```

### Step 3: API 호출 후 통계 확인

```sql
-- ⚠️ 모니터링 쿼리 (성능 분석 전용)
-- 주의: LIKE 패턴 매칭은 분석용입니다.

-- 가장 많이 실행된 쿼리 Top 10
SELECT
    SUBSTRING(DIGEST_TEXT, 1, 100) AS query_preview,
    COUNT_STAR AS exec_count,
    SUM_TIMER_WAIT/1000000000 AS total_time_ms,
    AVG_TIMER_WAIT/1000000000 AS avg_time_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE DIGEST_TEXT LIKE '%order_items%'  -- ⚠️ 중간 매칭 (분석 전용)
   OR DIGEST_TEXT LIKE '%orders%'
   OR DIGEST_TEXT LIKE '%products%'
ORDER BY COUNT_STAR DESC
LIMIT 10;
```

**✅ Batch Fetch 동작:**
```
+-----------------------------------------------------+------------+--------------+-------------+
| query_preview                                       | exec_count | total_time  | avg_time_ms |
+-----------------------------------------------------+------------+--------------+-------------+
| SELECT ... FROM `orders` WHERE `user_id` = ?        |          1 |        5.23 |        5.23 |
| SELECT ... FROM `order_items` WHERE `order_id` IN   |          1 |        3.45 |        3.45 |
| SELECT ... FROM `products` WHERE `id` IN            |          1 |        2.11 |        2.11 |
+-----------------------------------------------------+------------+--------------+-------------+
```

**❌ N+1 문제:**
```
+-----------------------------------------------------+------------+--------------+-------------+
| query_preview                                       | exec_count | total_time  | avg_time_ms |
+-----------------------------------------------------+------------+--------------+-------------+
| SELECT ... FROM `orders` WHERE `user_id` = ?        |          1 |        5.23 |        5.23 |
| SELECT ... FROM `order_items` WHERE `order_id` = ?  |         10 |       34.50 |        3.45 |
| SELECT ... FROM `products` WHERE `id` = ?           |         30 |       63.30 |        2.11 |
+-----------------------------------------------------+------------+--------------+-------------+
exec_count가 10, 30으로 많음! ← N+1 문제
```

---

## 방법 4: MySQL Workbench Visual Explain

### Step 1: Query Tab에서 쿼리 입력

```sql
-- 예: IN 절을 사용한 Batch 쿼리
SELECT oi.*, p.name, p.price
FROM order_items oi
JOIN products p ON oi.product_id = p.id
WHERE oi.order_id IN (1, 2, 3, 4, 5);
```

### Step 2: "Execution Plan" 버튼 클릭

Workbench에서 Visual Explain 화면이 나타남:

**✅ 좋은 플랜:**
```
[Index Range Scan]
  ↓ (idx_order_id 사용)
[Join]
  ↓ (idx_product_id 사용)
[Result]

Cost: 15.5
Rows: 25
```

**❌ 나쁜 플랜:**
```
[Full Table Scan]
  ↓ (인덱스 미사용)
[Join]
  ↓ (Full scan)
[Result]

Cost: 1550.0
Rows: 10000
```

---

## 방법 5: Slow Query Log 활용

### Step 1: Slow Query Log 설정

```sql
-- 0.1초 이상 쿼리 기록
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.1;
SET GLOBAL log_output = 'TABLE';
```

### Step 2: API 호출 후 확인

```sql
SELECT
    sql_text,
    query_time,
    lock_time,
    rows_examined,
    rows_sent
FROM mysql.slow_log
WHERE start_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)
ORDER BY query_time DESC;
```

**N+1 문제가 있으면:**
- 같은 패턴의 쿼리가 여러 번 기록됨
- 개별 쿼리는 빠르지만, 누적 시간이 김

---

## 🎯 실전 시나리오: 단계별 검증

### 1단계: General Log로 쿼리 개수 확인

```bash
# 터미널 1: 로그 초기화
mysql -u root -p -e "TRUNCATE TABLE mysql.general_log; SET GLOBAL general_log = 'ON';"

# 터미널 2: API 호출
curl "http://localhost:8080/api/orders?userId=1"

# 터미널 1: 쿼리 개수 확인
mysql -u root -p -e "
SELECT COUNT(*) AS total_queries
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 10 SECOND);
"
```

**기대 결과:**
- ✅ Batch: 3~5개 쿼리
- ❌ N+1: 10개 이상 쿼리

### 2단계: IN 절 사용 여부 확인

```sql
SELECT argument
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE '%order_items%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 10 SECOND)
ORDER BY event_time;
```

**✅ Batch Fetch:**
```sql
-- IN 절 사용!
SELECT ... FROM order_items WHERE order_id IN (1, 2, 3, 4, 5)
```

**❌ N+1:**
```sql
-- 개별 쿼리 반복!
SELECT ... FROM order_items WHERE order_id = 1
SELECT ... FROM order_items WHERE order_id = 2
SELECT ... FROM order_items WHERE order_id = 3
```

### 3단계: 인덱스 사용 확인

```sql
-- order_items 테이블 인덱스 확인
SHOW INDEX FROM order_items WHERE Key_name = 'idx_order_id';
```

```sql
-- 실제 쿼리에서 인덱스 사용 여부
EXPLAIN
SELECT * FROM order_items WHERE order_id IN (1, 2, 3, 4, 5);
```

---

## 📊 결과 비교표

| 지표 | N+1 문제 | Batch Fetch |
|------|----------|-------------|
| 총 쿼리 수 | 41개 | 3개 |
| order_items 쿼리 | 10개 (개별) | 1개 (IN) |
| products 쿼리 | 30개 (개별) | 1개 (IN) |
| 총 실행 시간 | ~100ms | ~10ms |
| 인덱스 사용 | Range × 40 | Range × 2 |

---

## 🛠️ 실용적인 스크립트

### 원클릭 검증 스크립트

```sql
-- verify_n1.sql
-- 사용법: mysql -u root -p < verify_n1.sql

-- 1. 준비
TRUNCATE TABLE mysql.general_log;
SET GLOBAL general_log = 'ON';

-- 2. 10초 대기 (이 사이에 API 호출)
SELECT 'API를 호출하세요! (10초 후 자동 분석)' AS message;
DO SLEEP(10);

-- 3. 분석
SELECT
    '=== 쿼리 개수 분석 ===' AS section,
    COUNT(*) AS total_select_queries,
    SUM(CASE WHEN argument LIKE '%IN (%' THEN 1 ELSE 0 END) AS batch_queries,
    SUM(CASE WHEN argument LIKE '%IN (%' THEN 0 ELSE 1 END) AS individual_queries
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 15 SECOND);

-- 4. 상세 쿼리 목록
SELECT
    '=== 실행된 쿼리 목록 ===' AS section,
    SUBSTRING(argument, 1, 80) AS query_preview,
    CASE WHEN argument LIKE '%IN (%' THEN 'Batch' ELSE 'Individual' END AS type
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 15 SECOND)
ORDER BY event_time;

-- 5. 판정
SELECT
    CASE
        WHEN (SELECT COUNT(*) FROM mysql.general_log
              WHERE command_type = 'Query'
                AND argument LIKE 'select%'
                AND event_time >= DATE_SUB(NOW(), INTERVAL 15 SECOND)) <= 5
        THEN '✅ PASS - Batch Fetch 동작 중!'
        ELSE '❌ FAIL - N+1 문제 존재'
    END AS result;
```

---

## ✅ 체크리스트

실제 DB에서 확인:
- [ ] General Log에서 총 SELECT 쿼리 5개 이하
- [ ] order_items 쿼리에 `IN (?, ?, ...)` 포함
- [ ] products 쿼리에 `IN (?, ?, ...)` 포함
- [ ] EXPLAIN 결과에서 `idx_order_id`, `idx_product_id` 사용 확인
- [ ] Performance Schema에서 exec_count가 1~3 정도

---

## 🚀 지금 바로 실행하기

```bash
# 1. MySQL Workbench 열기
# 2. ecommerce DB 선택
# 3. 아래 쿼리 실행

TRUNCATE TABLE mysql.general_log;
SET GLOBAL general_log = 'ON';

# 4. 터미널에서
curl "http://localhost:8080/api/orders?userId=1"

# 5. Workbench로 돌아와서
SELECT
    SUBSTRING(argument, 1, 100) AS query,
    COUNT(*) OVER() AS total_queries
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND)
ORDER BY event_time;
```

총 쿼리가 3~5개면 성공! 🎉

---

## 🏗️ 프로덕션 코드에서의 올바른 패턴

위의 모니터링 쿼리는 검증용이며, **실제 프로덕션 코드에서는 다음 원칙을 따라야 합니다**.

### ❌ 잘못된 패턴 (인덱스 미활용)

#### 문제 1: 함수 사용으로 인한 인덱스 미활용

```sql
-- ❌ BAD: 함수 사용으로 인덱스 사용 불가
SELECT * FROM orders
WHERE paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY);

-- ❌ BAD: LIKE 중간 매칭
WHERE argument LIKE '%FROM orders%';
```

**문제점**:
- `DATE_SUB(NOW(), ...)`: 비교 대상에 함수 사용 → 인덱스 미활용
- `LIKE '%text%'`: 중간 매칭 → 인덱스 미활용
- 데이터 증가 시 Full Table Scan 발생

#### 문제 2: 실시간 집계 + GROUP BY

```sql
-- ❌ BAD: 매번 실시간 집계
SELECT
    oi.product_id,
    COUNT(*) AS sales_count
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)  -- 함수!
GROUP BY oi.product_id
ORDER BY sales_count DESC;  -- 계산 컬럼!
```

**문제점**:
1. 함수 사용 → 인덱스 미활용
2. 매번 GROUP BY → CPU 부하
3. 계산 컬럼 정렬 → filesort 발생

---

### ✅ 올바른 패턴 (인덱스 최적화)

프로덕션 코드에서 실제 적용된 패턴입니다.

#### 패턴 1: 파라미터 사용 (함수 제거)

**실제 코드**: [`JpaProductSalesAggregateRepository.java:39-40`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

```sql
-- ✅ GOOD: 파라미터 사용
SELECT
    product_id AS productId,
    product_name AS productName,
    SUM(sales_count) AS salesCount,
    SUM(revenue) AS revenue
FROM product_sales_aggregates
WHERE aggregation_date >= :startDate    -- ✅ 파라미터 (함수 X)
  AND aggregation_date <= :endDate      -- ✅ 파라미터 (함수 X)
GROUP BY product_id, product_name
ORDER BY salesCount DESC
LIMIT 5
```

**Java 코드 예시**:
```java
// 애플리케이션 레이어에서 날짜 계산
LocalDate endDate = LocalDate.now();
LocalDate startDate = endDate.minusDays(3);

// ✅ 파라미터로 전달 (DB에서 함수 사용 X)
List<TopProductProjection> topProducts =
    repository.findTopProductsByDateRange(startDate, endDate);
```

**개선 효과**:
- ✅ 인덱스 range scan 가능
- ✅ EXPLAIN: `type: range`, `key: idx_date_sales`

---

#### 패턴 2: 동등 조건 (최고 성능)

**실제 코드**: [`JpaProductSalesAggregateRepository.java:67`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

```sql
-- ✅ BEST: 동등 조건 (인덱스 100% 활용)
SELECT
    product_id AS productId,
    product_name AS productName,
    sales_count AS salesCount,
    revenue AS revenue
FROM product_sales_aggregates
WHERE aggregation_date = :date    -- ✅ 동등 조건!
ORDER BY sales_count DESC         -- ✅ 인덱스 컬럼!
LIMIT 5
```

**Java 코드 예시**:
```java
// 오늘의 인기 상품
LocalDate today = LocalDate.now();
List<TopProductProjection> topProducts =
    repository.findTopProductsByDate(today);
```

**개선 효과**:
- ✅ 인덱스 100% 활용 (`type: ref`)
- ✅ 인덱스의 `sales_count DESC` 순서 활용 → filesort 없음
- ✅ 실행 시간 <1ms

**인덱스 전략**:
```sql
-- 복합 인덱스로 정렬까지 커버
CREATE INDEX idx_date_sales
ON product_sales_aggregates (aggregation_date, sales_count DESC);
```

---

#### 패턴 3: IN 조건 (여러 동등 조건)

**실제 코드**: [`JpaProductSalesAggregateRepository.java:90`](../src/main/java/io/hhplus/ecommerce/infrastructure/persistence/product/JpaProductSalesAggregateRepository.java)

```sql
-- ✅ GOOD: IN 조건 (여러 동등 조건의 집합)
SELECT
    product_id AS productId,
    product_name AS productName,
    SUM(sales_count) AS salesCount,
    SUM(revenue) AS revenue
FROM product_sales_aggregates
WHERE aggregation_date IN :dates    -- ✅ IN 조건!
GROUP BY product_id, product_name
ORDER BY salesCount DESC
LIMIT 5
```

**Java 코드 예시**:
```java
// 최근 3일간 인기 상품
LocalDate today = LocalDate.now();
List<LocalDate> dates = List.of(
    today.minusDays(2),
    today.minusDays(1),
    today
);

List<TopProductProjection> topProducts =
    repository.findTopProductsByDates(dates);
```

**개선 효과**:
- ✅ 여러 동등 조건 → 범위 조건보다 효율적
- ✅ 적은 데이터셋 (3일 * 상품수) → GROUP BY 부담 적음

---

#### 패턴 4: ROLLUP 전략 (사전 집계)

**문제**: 실시간 집계는 데이터 증가 시 성능 저하

**해결**: 배치로 사전 집계 → 집계 테이블 조회

**실제 구현**: [`ProductSalesAggregate.java`](../src/main/java/io/hhplus/ecommerce/domain/product/ProductSalesAggregate.java)

```java
@Entity
@Table(
    name = "product_sales_aggregates",
    indexes = {
        @Index(name = "idx_date_sales",
               columnList = "aggregation_date, sales_count DESC"),
        @Index(name = "idx_product_date",
               columnList = "product_id, aggregation_date")
    }
)
public class ProductSalesAggregate extends BaseTimeEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "aggregation_date", nullable = false)
    private LocalDate aggregationDate;  // ✅ 인덱스 컬럼

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;  // ✅ 인덱스 컬럼 (DESC)

    @Column(name = "revenue", nullable = false)
    private Long revenue;
}
```

**배치 집계 (일일 실행)**:
```java
@Scheduled(cron = "0 0 0 * * *")  // 매일 자정
public void aggregateDailySales() {
    LocalDate yesterday = LocalDate.now().minusDays(1);

    // 1. 어제 판매 데이터 집계
    List<SalesData> salesData =
        orderRepository.findSalesByDate(yesterday);

    // 2. 집계 테이블에 저장
    salesData.forEach(data -> {
        ProductSalesAggregate aggregate = ProductSalesAggregate.create(
            data.getProductId(),
            data.getProductName(),
            yesterday,  // ✅ 정확한 날짜
            data.getSalesCount(),
            data.getRevenue()
        );
        aggregateRepository.save(aggregate);
    });
}
```

**개선 효과**:
- ✅ 원본 테이블(orders, order_items) 부하 없음
- ✅ 단일 테이블 조회 → 빠른 응답
- ✅ 데이터 증가 무관 → 확장성 우수

---

### 📊 성능 비교표

| 항목 | ❌ 잘못된 패턴 | ✅ 올바른 패턴 |
|------|---------------|---------------|
| **WHERE 조건** | `DATE_SUB(NOW(), ...)` | `:startDate`, `:endDate` |
| **인덱스 활용** | 0% (Full scan) | 100% (Index scan) |
| **EXPLAIN type** | `ALL` | `range` / `ref` |
| **실행 시간** | ~100ms | <1ms |
| **데이터 증가 영향** | 선형 증가 (N) | 거의 없음 (log N) |
| **CPU 부하** | 높음 (GROUP BY 매번) | 낮음 (사전 집계) |

---

### 🎯 코치 피드백 반영 체크리스트

율무 코치님 피드백 완전 반영:

- [x] **함수 제거**: `DATE_SUB(NOW(), ...)` → `:startDate`, `:endDate` 파라미터
- [x] **동등 조건 우선**: `aggregation_date = :date` 쿼리 추가
- [x] **IN 조건 활용**: `aggregation_date IN :dates` 쿼리 추가
- [x] **ROLLUP 전략**: ProductSalesAggregate 집계 테이블 구현
- [x] **인덱스 최적화**: `idx_date_sales`, `idx_product_date` 생성
- [x] **문서화**: 올바른 패턴 가이드 작성

**참고 문서**:
- [QUERY_OPTIMIZATION_SUMMARY.md](../docs/week4/verification/QUERY_OPTIMIZATION_SUMMARY.md) - 쿼리 최적화 상세 가이드
- [EXPLAIN_ANALYZE_GUIDE.md](../docs/week4/verification/EXPLAIN_ANALYZE_GUIDE.md) - 쿼리 실행 계획 분석

---

### 💡 핵심 원칙 요약

#### ✅ DO (권장)
1. **파라미터 사용**: 애플리케이션에서 날짜 계산 → 파라미터로 전달
2. **동등 조건 우선**: `=` 조건이 가장 빠름 → 범위 조건보다 우선
3. **IN 조건 활용**: 여러 특정 값 조회 시 IN 사용
4. **사전 집계**: 배치로 미리 집계 → 조회 시 부하 최소화
5. **인덱스 설계**: WHERE, ORDER BY 컬럼 모두 커버하는 복합 인덱스

#### ❌ DON'T (금지)
1. **함수 사용 금지**: WHERE 절에 `DATE_SUB()`, `NOW()`, `CURDATE()` 사용 금지
2. **LIKE 중간 매칭**: `LIKE '%text%'` 사용 금지 (Full scan)
3. **실시간 집계 지양**: 대용량 테이블 GROUP BY 반복 실행 금지
4. **계산 컬럼 정렬**: `ORDER BY COUNT(*)` 지양 (인덱스 활용 불가)

---

### 🔍 EXPLAIN으로 검증하기

올바른 패턴이 적용되었는지 EXPLAIN으로 확인:

```sql
EXPLAIN
SELECT
    product_id,
    product_name,
    sales_count,
    revenue
FROM product_sales_aggregates
WHERE aggregation_date = '2025-11-19'  -- ✅ 동등 조건
ORDER BY sales_count DESC
LIMIT 5;
```

**기대 결과**:
```
+----+-------------+-------+-------+---------------+---------------+---------+-------+------+-------+
| id | select_type | table | type  | possible_keys | key           | key_len | ref   | rows | Extra |
+----+-------------+-------+-------+---------------+---------------+---------+-------+------+-------+
|  1 | SIMPLE      | ...   | ref   | idx_date_...  | idx_date_...  | 3       | const |   50 | ...   |
+----+-------------+-------+-------+---------------+---------------+---------+-------+------+-------+
```

**✅ 좋은 신호**:
- `type: ref` (동등 조건 인덱스 조회)
- `key: idx_date_sales` (인덱스 사용)
- `rows: 50` (적은 행 스캔)
- `Extra: Using index` (커버링 인덱스, 가능한 경우)

**❌ 나쁜 신호**:
- `type: ALL` → Full table scan
- `key: NULL` → 인덱스 미사용
- `rows: 10000+` → 많은 행 스캔
- `Extra: Using filesort` → 정렬 부하
