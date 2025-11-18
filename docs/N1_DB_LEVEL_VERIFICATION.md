# N+1 문제 DB 레벨에서 확인하기

## 🎯 목표
MySQL Workbench, EXPLAIN, Performance Schema를 사용해서 N+1 문제 해결을 객관적으로 검증

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
-- 최근 실행된 SELECT 쿼리 보기
SELECT
    event_time,
    argument,
    SUBSTRING(argument, 1, 100) AS query_preview
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
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
-- SELECT 쿼리 패턴별 개수 확인
SELECT
    CASE
        WHEN argument LIKE '%FROM orders%' THEN 'orders'
        WHEN argument LIKE '%FROM order_items%' THEN 'order_items'
        WHEN argument LIKE '%FROM products%' THEN 'products'
        ELSE 'other'
    END AS query_type,
    COUNT(*) AS query_count,
    CASE
        WHEN argument LIKE '%IN (%' THEN 'Batch (IN clause)'
        ELSE 'Individual'
    END AS fetch_type
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)
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
-- 가장 많이 실행된 쿼리 Top 10
SELECT
    SUBSTRING(DIGEST_TEXT, 1, 100) AS query_preview,
    COUNT_STAR AS exec_count,
    SUM_TIMER_WAIT/1000000000 AS total_time_ms,
    AVG_TIMER_WAIT/1000000000 AS avg_time_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE DIGEST_TEXT LIKE '%order_items%'
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
