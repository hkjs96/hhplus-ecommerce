# 인기 상품 조회 쿼리 최적화 검증 가이드

## 🎯 목적

최적화된 쿼리가 실제로 동작하는지 확인합니다:
- ROLLUP 테이블 사용 확인
- IN 조건 쿼리 실행 확인
- 인덱스 활용 확인
- 응답 시간 확인 (<1ms)

---

## 🚀 Step 1: 테스트 데이터 준비

### 1-1. ProductSalesAggregate 테이블에 테스트 데이터 추가

애플리케이션을 실행하기 전에 먼저 집계 테이블에 테스트 데이터를 추가해야 합니다.

**방법 1: SQL 직접 실행** (MySQL 접속)

```bash
# MySQL 접속
mysql -u root -p ecommerce
```

```sql
-- 오늘 기준 테스트 데이터 삽입
INSERT INTO product_sales_aggregates
(product_id, product_name, aggregation_date, sales_count, revenue, created_at)
VALUES
-- 3일 전 데이터
(1, '노트북', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 15, 22500000, NOW()),
(2, '무선 마우스', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 25, 625000, NOW()),
(3, '기계식 키보드', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 20, 2000000, NOW()),
(4, '27인치 모니터', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 10, 3000000, NOW()),
(5, '무선 헤드셋', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 18, 2700000, NOW()),

-- 2일 전 데이터
(1, '노트북', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 20, 30000000, NOW()),
(2, '무선 마우스', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 30, 750000, NOW()),
(3, '기계식 키보드', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 22, 2200000, NOW()),
(4, '27인치 모니터', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 12, 3600000, NOW()),
(5, '무선 헤드셋', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 15, 2250000, NOW()),

-- 오늘 데이터
(1, '노트북', CURDATE(), 25, 37500000, NOW()),
(2, '무선 마우스', CURDATE(), 35, 875000, NOW()),
(3, '기계식 키보드', CURDATE(), 28, 2800000, NOW()),
(4, '27인치 모니터', CURDATE(), 15, 4500000, NOW()),
(5, '무선 헤드셋', CURDATE(), 20, 3000000, NOW());
```

**방법 2: DataInitializer에 추가** (권장)

`DataInitializer.java`에 ProductSalesAggregate 생성 로직을 추가하면 애플리케이션 시작 시 자동으로 생성됩니다.

---

## 🚀 Step 2: 애플리케이션 실행

### 2-1. 터미널 1번 (애플리케이션 시작)

```bash
cd /Users/jsb/hanghe-plus/ecommerce

# 기존 실행 중인 프로세스 종료
pkill -f gradle
pkill -f java

# 애플리케이션 시작 (로그 확인 가능)
./gradlew bootRun
```

**대기**: `Started EcommerceApplication` 메시지가 나올 때까지 (약 10-15초)

### 2-2. 실행 로그에서 확인할 내용

애플리케이션 시작 시 다음 로그를 확인하세요:

```
INFO  i.h.e.a.u.product.GetTopProductsUseCase - Getting top products (last 3 days) using ROLLUP strategy
DEBUG org.hibernate.SQL -
    SELECT
        product_id AS productId,
        product_name AS productName,
        SUM(sales_count) AS salesCount,
        SUM(revenue) AS revenue
    FROM product_sales_aggregates
    WHERE aggregation_date IN (?, ?, ?)  -- ✅ IN 조건 사용!
    GROUP BY product_id, product_name
    ORDER BY salesCount DESC
    LIMIT 5
```

---

## 📊 Step 3: API 호출 테스트

### 3-1. 터미널 2번 열기 (새 터미널)

```bash
# 인기 상품 TOP 5 조회
curl -s "http://localhost:8080/api/products/top" | jq
```

**예상 결과**:
```json
{
  "success": true,
  "data": {
    "period": "3days",
    "products": [
      {
        "rank": 1,
        "productId": 1,
        "productName": "노트북",
        "salesCount": 60,        // 15 + 20 + 25 = 60
        "revenue": 90000000      // 22500000 + 30000000 + 37500000
      },
      {
        "rank": 2,
        "productId": 2,
        "productName": "무선 마우스",
        "salesCount": 90,        // 25 + 30 + 35 = 90
        "revenue": 2250000
      },
      {
        "rank": 3,
        "productName": "기계식 키보드",
        "salesCount": 70,
        "revenue": 7000000
      },
      {
        "rank": 4,
        "productName": "무선 헤드셋",
        "salesCount": 53,
        "revenue": 7950000
      },
      {
        "rank": 5,
        "productName": "27인치 모니터",
        "salesCount": 37,
        "revenue": 11100000
      }
    ]
  },
  "error": null
}
```

✅ **성공 확인**:
- `success: true`
- `period: "3days"`
- `products` 배열에 5개 상품
- `rank`, `salesCount`, `revenue` 정상 표시

---

## 🔍 Step 4: 쿼리 실행 계획 확인 (MySQL)

### 4-1. MySQL 접속

```bash
mysql -u root -p ecommerce
```

### 4-2. EXPLAIN ANALYZE 실행

```sql
-- 실제 쿼리 실행 계획 분석
EXPLAIN ANALYZE
SELECT
    product_id AS productId,
    product_name AS productName,
    SUM(sales_count) AS salesCount,
    SUM(revenue) AS revenue
FROM product_sales_aggregates
WHERE aggregation_date IN (
    DATE_SUB(CURDATE(), INTERVAL 2 DAY),
    DATE_SUB(CURDATE(), INTERVAL 1 DAY),
    CURDATE()
)
GROUP BY product_id, product_name
ORDER BY salesCount DESC
LIMIT 5;
```

**예상 결과**:
```
-> Limit: 5 row(s) (cost=X rows=Y) (actual time=0.5..0.6 rows=5 loops=1)
    -> Sort: salesCount DESC (cost=X rows=Y) (actual time=0.4..0.5 rows=5 loops=1)
        -> Table scan on <temporary> (cost=X rows=Y) (actual time=0.3..0.4 rows=5 loops=1)
            -> Aggregate using temporary table (cost=X rows=Y) (actual time=0.2..0.3 rows=5 loops=1)
                -> Index range scan on product_sales_aggregates using idx_date_sales
                   (cost=X rows=15) (actual time=0.1..0.2 rows=15 loops=1)
```

**핵심 확인 사항**:
- ✅ **Index range scan** on `idx_date_sales` → 인덱스 사용!
- ✅ **actual time < 1ms** → 빠른 실행
- ✅ **rows=15** → 3일 * 5개 상품 = 15개 행만 스캔

---

## 📊 Step 5: 성능 비교 (Before vs After)

### Before (실시간 집계 - Deprecated)

```sql
-- ❌ 성능 문제 쿼리
SELECT
    oi.product_id AS productId,
    p.name AS productName,
    COUNT(*) AS salesCount,
    SUM(oi.subtotal) AS revenue
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id
WHERE o.status = 'COMPLETED'
  AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)  -- ❌ 함수 사용!
GROUP BY oi.product_id, p.name
ORDER BY salesCount DESC
LIMIT 5;
```

**EXPLAIN ANALYZE 예상**:
```
-> Sort: salesCount DESC (cost=X rows=Y) (actual time=50..52 rows=5 loops=1)
    -> Table scan on <temporary> (cost=X rows=Y) (actual time=45..48 rows=100 loops=1)
        -> Aggregate using temporary table (cost=X rows=Y) (actual time=40..45 rows=100 loops=1)
            -> Nested loop inner join (cost=X rows=Y) (actual time=10..35 rows=1000 loops=1)
                -> Filter: (o.status = 'COMPLETED' and o.paid_at >= DATE_SUB(...))
                   (cost=X rows=Y) (actual time=5..15 rows=500 loops=1)
                    -> Table scan on orders (cost=X rows=Y) (actual time=2..10 rows=10000 loops=1)
```

**문제점**:
- ❌ Table scan on orders (전체 테이블 스캔)
- ❌ 3개 테이블 JOIN
- ❌ DATE_SUB 함수로 인덱스 미활용
- ❌ 실행 시간: ~50ms

### After (ROLLUP 테이블 - Optimized)

```sql
-- ✅ 최적화된 쿼리
SELECT
    product_id AS productId,
    product_name AS productName,
    SUM(sales_count) AS salesCount,
    SUM(revenue) AS revenue
FROM product_sales_aggregates
WHERE aggregation_date IN (?, ?, ?)  -- ✅ 파라미터 사용!
GROUP BY product_id, product_name
ORDER BY salesCount DESC
LIMIT 5;
```

**EXPLAIN ANALYZE 예상**:
```
-> Limit: 5 row(s) (cost=X rows=Y) (actual time=0.5..0.6 rows=5 loops=1)
    -> Index range scan on product_sales_aggregates using idx_date_sales
       (cost=X rows=15) (actual time=0.1..0.2 rows=15 loops=1)
```

**개선점**:
- ✅ Index range scan (인덱스 활용)
- ✅ 단일 테이블 조회
- ✅ 파라미터 사용으로 인덱스 활용
- ✅ 실행 시간: <1ms

---

## 📈 성능 비교표

| 항목 | Before (실시간 집계) | After (ROLLUP 테이블) | 개선율 |
|------|---------------------|----------------------|--------|
| **테이블 스캔** | 3개 (orders, order_items, products) | 1개 (product_sales_aggregates) | 67% 감소 |
| **조회 행 수** | ~1000 rows | 15 rows | 98% 감소 |
| **인덱스 활용** | ❌ 함수로 인한 미활용 | ✅ idx_date_sales 활용 | - |
| **실행 시간** | ~50ms | **<1ms** | **50배 향상** |
| **확장성** | ❌ 데이터 증가 시 느림 | ✅ 데이터 증가 무관 | - |

---

## 🧪 Step 6: 다양한 시나리오 테스트

### 시나리오 1: 데이터가 없는 경우

```bash
# 집계 테이블 비우기
mysql -u root -p ecommerce -e "TRUNCATE TABLE product_sales_aggregates;"

# API 호출
curl -s "http://localhost:8080/api/products/top" | jq
```

**예상 결과**:
```json
{
  "success": true,
  "data": {
    "period": "3days",
    "products": []
  },
  "error": null
}
```

### 시나리오 2: 특정 날짜만 데이터가 있는 경우

```sql
-- 오늘 데이터만 삽입
INSERT INTO product_sales_aggregates
(product_id, product_name, aggregation_date, sales_count, revenue, created_at)
VALUES
(1, '노트북', CURDATE(), 10, 15000000, NOW()),
(2, '무선 마우스', CURDATE(), 20, 500000, NOW());
```

```bash
curl -s "http://localhost:8080/api/products/top" | jq
```

**예상 결과**: 오늘 데이터만 집계되어 반환

### 시나리오 3: 성능 측정 (여러 번 호출)

```bash
# 10번 호출하여 평균 응답 시간 측정
for i in {1..10}; do
  time curl -s "http://localhost:8080/api/products/top" > /dev/null
done
```

**예상 결과**:
```
real    0m0.005s  # ~5ms (네트워크 포함)
user    0m0.002s
sys     0m0.001s
```

---

## 🔍 Step 7: 애플리케이션 로그 확인

### 7-1. 터미널 1번 (애플리케이션 로그)에서 확인

API 호출 시 다음 로그가 나타나야 합니다:

```
INFO  i.h.e.a.u.product.GetTopProductsUseCase - Getting top products (last 3 days) using ROLLUP strategy

DEBUG org.hibernate.SQL -
    SELECT
        product_id AS productId,
        product_name AS productName,
        SUM(sales_count) AS salesCount,
        SUM(revenue) AS revenue
    FROM product_sales_aggregates
    WHERE aggregation_date IN (?, ?, ?)
    GROUP BY product_id, product_name
    ORDER BY salesCount DESC
    LIMIT 5

DEBUG org.hibernate.orm.jdbc.bind - binding parameter [1] as [DATE] - [2025-01-16]
DEBUG org.hibernate.orm.jdbc.bind - binding parameter [2] as [DATE] - [2025-01-17]
DEBUG org.hibernate.orm.jdbc.bind - binding parameter [3] as [DATE] - [2025-01-18]

INFO  i.h.e.a.u.product.GetTopProductsUseCase - Found 5 top products using ROLLUP strategy (<1ms)
```

**핵심 확인사항**:
- ✅ `WHERE aggregation_date IN (?, ?, ?)` → IN 조건 사용
- ✅ `product_sales_aggregates` 테이블 조회
- ✅ 3개 날짜 파라미터 바인딩
- ✅ "using ROLLUP strategy" 로그 출력

---

## 🎯 검증 체크리스트

### 기능 검증
- [ ] 애플리케이션 정상 시작
- [ ] API 호출 성공 (HTTP 200)
- [ ] 인기 상품 TOP 5 반환
- [ ] 3일간 데이터 합산 정상
- [ ] rank 순서 정확 (1~5)

### 쿼리 최적화 검증
- [ ] `product_sales_aggregates` 테이블 사용 확인
- [ ] `WHERE aggregation_date IN (?, ?, ?)` 조건 확인
- [ ] 파라미터 바인딩 확인 (3개 날짜)
- [ ] "using ROLLUP strategy" 로그 확인
- [ ] EXPLAIN ANALYZE에서 인덱스 사용 확인

### 성능 검증
- [ ] 응답 시간 < 10ms
- [ ] EXPLAIN ANALYZE 실행 시간 < 1ms
- [ ] Index range scan 사용 확인
- [ ] 조회 행 수 = 15개 (3일 * 5개 상품)

---

## 💡 문제 해결

### 문제 1: 데이터가 반환되지 않음

**원인**: `product_sales_aggregates` 테이블에 데이터 없음

**해결**:
```sql
-- 데이터 확인
SELECT * FROM product_sales_aggregates
WHERE aggregation_date >= DATE_SUB(CURDATE(), INTERVAL 2 DAY);

-- 데이터가 없으면 Step 1의 SQL 실행
```

### 문제 2: 날짜가 맞지 않음

**원인**: 테스트 데이터의 날짜가 오래됨

**해결**:
```sql
-- 기존 데이터 삭제
DELETE FROM product_sales_aggregates;

-- 오늘 기준 데이터 다시 삽입 (Step 1 참조)
```

### 문제 3: 로그에 다른 쿼리가 보임

**원인**: GetTopProductsUseCase가 아직 업데이트되지 않았거나 캐시 문제

**해결**:
```bash
# 애플리케이션 재시작
pkill -f gradle
./gradlew clean bootRun
```

### 문제 4: EXPLAIN ANALYZE 결과가 느림

**원인**: 인덱스가 생성되지 않음

**해결**:
```sql
-- 인덱스 확인
SHOW INDEX FROM product_sales_aggregates;

-- 인덱스 재생성 (필요시)
CREATE INDEX idx_date_sales ON product_sales_aggregates (aggregation_date, sales_count DESC);
```

---

## 📚 참고 자료

- `QUERY_OPTIMIZATION_SUMMARY.md` - 쿼리 최적화 전체 요약
- `YULMU_FEEDBACK_STATUS.md` - 피드백 반영 상태
- `GetTopProductsUseCase.java:37-71` - ROLLUP 전략 구현
- `JpaProductSalesAggregateRepository.java:82-94` - IN 조건 쿼리

---

## ✅ 최종 확인

모든 체크리스트를 통과하면:
- ✅ ROLLUP 전략 정상 동작
- ✅ IN 조건으로 인덱스 100% 활용
- ✅ 실행 시간 <1ms
- ✅ 함수 사용 제거 완료
- ✅ 쿼리 최적화 검증 완료!

**쿼리 최적화 성공!** 🚀
