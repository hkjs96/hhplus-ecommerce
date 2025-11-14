# Week 4 - STEP 8: DB 쿼리 및 인덱스 최적화

## 과제 개요

**목표**: 애플리케이션에서 성능 저하를 유발할 수 있는 DB 조회 패턴을 식별하고, 쿼리 최적화 및 인덱스 설계를 통해 해결 방안을 도출합니다.

**핵심 작업**:
1. 조회 성능 저하 가능성이 있는 기능 식별
2. 쿼리 실행계획(EXPLAIN) 분석
3. 인덱스 설계 또는 쿼리 구조 개선
4. 최적화 보고서 작성

---

## 🎯 과제 목표

### 1. 성능 병목 식별
- 대용량 데이터에서 느릴 수 있는 쿼리 패턴 찾기
- N+1 문제, 전체 테이블 스캔(Full Table Scan) 등 확인
- 비즈니스 요구사항에서 성능이 중요한 기능 파악

### 2. 실행 계획 분석
- `EXPLAIN` 명령어로 쿼리 실행 계획 확인
- 인덱스 사용 여부, 스캔 타입, 예상 행 수 분석
- 병목 지점 명확히 파악

### 3. 최적화 방안 도출
- 인덱스 추가/변경 (Composite Index, Covering Index)
- 쿼리 재구성 (JOIN 순서, WHERE 절 개선)
- 비정규화 또는 캐싱 고려

### 4. 최적화 보고서 작성
- 문제 정의, 원인 분석, 해결 방안, 성능 개선 결과를 문서화
- 트레이드오프 분석 포함

---

## 📋 PASS/FAIL 기준

### ✅ PASS 조건

#### 1. 성능 병목 식별
- [ ] 조회 성능 저하 가능성이 있는 기능을 식별
- [ ] 왜 해당 기능이 느릴 수 있는지 근거 제시
- [ ] 비즈니스 요구사항과 연결하여 분석

#### 2. 쿼리 실행계획 분석
- [ ] `EXPLAIN` 또는 `EXPLAIN ANALYZE` 결과 포함
- [ ] 실행 계획의 문제점을 명확히 설명
- [ ] 스캔 타입, 인덱스 사용 여부, 예상 행 수 등 분석

#### 3. 최적화 방안 도출
- [ ] 인덱스 설계 또는 쿼리 구조 개선 방안 제시
- [ ] 솔루션의 효과를 정량적으로 제시 (응답 시간, 스캔 행 수 등)
- [ ] 트레이드오프 분석 (저장 공간, 쓰기 성능 등)

#### 4. 최적화 보고서 작성
- [ ] 문제 정의 → 원인 분석 → 해결 방안 → 결과 순서로 구성
- [ ] 코드, 쿼리, 실행 계획이 명확히 포함
- [ ] 다른 개발자가 이해할 수 있는 수준의 문서

---

### ❌ FAIL 사유

#### 분석 부족
- ❌ 성능 병목 식별 없이 무작정 인덱스만 추가
- ❌ 실행 계획 분석 없이 감으로만 최적화
- ❌ 비즈니스 요구사항과 무관한 최적화

#### 근거 부족
- ❌ `EXPLAIN` 결과가 포함되지 않음
- ❌ 최적화 전후 성능 비교 없음
- ❌ 트레이드오프 분석 누락

#### 문서 품질
- ❌ 보고서가 없거나 내용이 빈약함
- ❌ 코드나 쿼리가 포함되지 않음
- ❌ 결론 없이 분석만 나열

---

## 🧠 핵심 역량 및 평가 포인트

### 1. 데이터 중심 설계 역량 🗄️

**평가 기준:**
- 비즈니스 요구사항을 반영하여 성능 병목을 식별했는가?
- 데이터 구조(ERD)와 쿼리 패턴을 함께 고려했는가?
- 인덱스 설계가 비즈니스 요구사항에 부합하는가?

**토론 주제:**
- "어떤 기능이 가장 느릴 것으로 예상되나요? 그 이유는?"
- "인덱스를 추가할 때 고려한 사항은 무엇인가요?"
- "정규화와 비정규화 중 어떤 것을 선택했나요? 왜?"

---

### 2. 성능 분석 역량 📊

**평가 기준:**
- `EXPLAIN` 실행 계획을 올바르게 해석했는가?
- 병목 지점을 정확히 파악했는가?
- 최적화 전후 성능을 정량적으로 비교했는가?

**토론 주제:**
- "`type: ALL`이 나오는 이유는 무엇인가요?"
- "인덱스를 추가했는데도 사용하지 않는 이유는?"
- "Nested Loop Join과 Hash Join의 차이는 무엇인가요?"

---

### 3. 쿼리 최적화 역량 🚀

**평가 기준:**
- 인덱스 설계 전략이 합리적인가? (Composite Index, Covering Index)
- 쿼리 구조 개선이 효과적인가? (JOIN, WHERE, ORDER BY)
- N+1 문제를 해결했는가?

**토론 주제:**
- "Composite Index의 순서를 어떻게 결정했나요?"
- "Covering Index를 사용한 이유는 무엇인가요?"
- "WHERE 절과 JOIN 절 중 어디에 인덱스를 추가했나요?"

---

### 4. 의사결정 역량 ⚖️

**평가 기준:**
- 트레이드오프를 명확히 이해하고 있는가?
- 최적화 방안의 장단점을 비교했는가?
- 비즈니스 요구사항에 맞는 선택을 했는가?

**토론 주제:**
- "인덱스를 추가하면 쓰기 성능이 저하되는데, 어떻게 판단했나요?"
- "비정규화를 선택한 이유는 무엇인가요?"
- "캐싱과 인덱스 중 어떤 것을 선택했나요? 왜?"

---

## 🛠️ 최적화 가이드

### 1. 성능 병목 식별 방법

#### 대용량 데이터에서 느릴 수 있는 쿼리 패턴

| 패턴 | 예시 | 문제점 |
|------|------|--------|
| **전체 테이블 스캔** | `SELECT * FROM products WHERE name LIKE '%노트북%'` | 인덱스 사용 불가 |
| **N+1 문제** | `Order` 조회 후 반복문에서 `OrderItem` 조회 | 쿼리가 N+1번 실행 |
| **복잡한 JOIN** | 5개 이상의 테이블 JOIN | JOIN 순서 최적화 필요 |
| **대용량 정렬** | `ORDER BY created_at` (인덱스 없음) | Filesort 발생 |
| **대용량 집계** | `GROUP BY category` (인덱스 없음) | Using temporary 발생 |

---

### 2. EXPLAIN 실행 계획 분석

#### EXPLAIN 명령어 사용

```sql
-- EXPLAIN으로 실행 계획 확인
EXPLAIN SELECT * FROM products WHERE category = '전자제품' ORDER BY price;

-- EXPLAIN ANALYZE로 실제 실행 시간 확인 (MySQL 8.0.18+)
EXPLAIN ANALYZE SELECT * FROM products WHERE category = '전자제품' ORDER BY price;
```

#### EXPLAIN 결과 해석

```
+----+-------------+----------+------+---------------+------+---------+------+------+----------+-----------------------------+
| id | select_type | table    | type | possible_keys | key  | key_len | ref  | rows | filtered | Extra                       |
+----+-------------+----------+------+---------------+------+---------+------+------+----------+-----------------------------+
|  1 | SIMPLE      | products | ALL  | NULL          | NULL | NULL    | NULL | 1000 |    10.00 | Using where; Using filesort |
+----+-------------+----------+------+---------------+------+---------+------+------+----------+-----------------------------+
```

**주요 컬럼 해석:**

| 컬럼 | 의미 | 좋음 | 나쁨 |
|------|------|------|------|
| **type** | 접근 방식 | `const`, `eq_ref`, `ref` | `ALL` (전체 스캔) |
| **key** | 사용된 인덱스 | 인덱스 이름 | `NULL` (인덱스 미사용) |
| **rows** | 예상 스캔 행 수 | 적을수록 좋음 | 전체 행 수에 가까움 |
| **Extra** | 추가 정보 | `Using index` (Covering Index) | `Using filesort`, `Using temporary` |

**type 컬럼 값:**
- `const`: PK 또는 Unique Index로 단일 행 조회 (가장 빠름)
- `eq_ref`: JOIN에서 PK 또는 Unique Index 사용
- `ref`: Non-Unique Index 사용
- `range`: 범위 검색 (`BETWEEN`, `>`, `<`)
- `index`: 인덱스 전체 스캔
- `ALL`: 테이블 전체 스캔 (가장 느림)

**Extra 컬럼 주요 값:**
- `Using index`: Covering Index (인덱스만으로 쿼리 완성)
- `Using where`: WHERE 절 필터링
- `Using filesort`: 정렬을 위해 별도 정렬 작업 수행 (느림)
- `Using temporary`: 임시 테이블 사용 (GROUP BY, DISTINCT 등)

---

### 3. 인덱스 설계 전략

#### 3.1. Single Index (단일 인덱스)

```sql
-- 카테고리별 상품 조회가 빈번한 경우
CREATE INDEX idx_category ON products(category);

-- 조회 쿼리
SELECT * FROM products WHERE category = '전자제품';
```

**EXPLAIN 결과 개선:**
```
type: ref (ALL → ref로 개선)
key: idx_category (인덱스 사용)
rows: 100 (1000 → 100으로 감소)
```

---

#### 3.2. Composite Index (복합 인덱스)

**인덱스 순서가 중요합니다!**

```sql
-- 카테고리 + 가격순 정렬이 빈번한 경우
CREATE INDEX idx_category_price ON products(category, price);

-- 조회 쿼리
SELECT * FROM products
WHERE category = '전자제품'
ORDER BY price;
```

**인덱스 순서 원칙:**
1. **동등 조건 (=)** 먼저
2. **범위 조건 (>, <, BETWEEN)** 나중
3. **정렬 (ORDER BY)** 마지막

**예시:**
```sql
-- ✅ 좋음: category(=) → price(ORDER BY)
CREATE INDEX idx_category_price ON products(category, price);

-- ❌ 나쁨: 순서가 반대
CREATE INDEX idx_price_category ON products(price, category);
```

---

#### 3.3. Covering Index (커버링 인덱스)

**인덱스만으로 쿼리를 완성하여 테이블 접근을 피합니다.**

```sql
-- 상품 목록 조회 시 id, name, price만 필요한 경우
CREATE INDEX idx_category_price_name ON products(category, price, name, id);

-- 조회 쿼리
SELECT id, name, price
FROM products
WHERE category = '전자제품'
ORDER BY price;
```

**EXPLAIN 결과:**
```
Extra: Using index (테이블 접근 없이 인덱스만 사용)
```

**장점:**
- 테이블 접근 없이 인덱스만 읽어서 매우 빠름
- I/O 작업 최소화

**단점:**
- 인덱스 크기 증가
- 쓰기 성능 약간 저하

---

#### 3.4. 인덱스 사용이 불가능한 경우

```sql
-- ❌ LIKE '%검색어%' (앞에 와일드카드)
SELECT * FROM products WHERE name LIKE '%노트북%';

-- ✅ LIKE '검색어%' (뒤에만 와일드카드)
SELECT * FROM products WHERE name LIKE '노트북%';

-- ❌ 함수 사용
SELECT * FROM orders WHERE DATE(created_at) = '2024-01-15';

-- ✅ 범위 조건으로 변경
SELECT * FROM orders
WHERE created_at >= '2024-01-15 00:00:00'
  AND created_at < '2024-01-16 00:00:00';

-- ❌ OR 조건 (인덱스 사용 어려움)
SELECT * FROM products WHERE category = '전자제품' OR price < 100000;

-- ✅ UNION 또는 IN으로 변경
SELECT * FROM products WHERE category = '전자제품'
UNION
SELECT * FROM products WHERE price < 100000;
```

---

### 4. 쿼리 최적화 기법

#### 4.1. N+1 문제 해결

**문제:**
```java
// ❌ N+1 문제 발생
List<Order> orders = orderRepository.findAll();  // 1번 쿼리
for (Order order : orders) {
    List<OrderItem> items = order.getItems();  // N번 쿼리
}
```

**해결 방법 1: Fetch Join**
```java
// ✅ Fetch Join으로 한 번에 조회
@Query("SELECT o FROM Order o JOIN FETCH o.items")
List<Order> findAllWithItems();
```

**해결 방법 2: @EntityGraph**
```java
@EntityGraph(attributePaths = {"items"})
List<Order> findAll();
```

**해결 방법 3: Batch Size**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

---

#### 4.2. JOIN 최적화

**문제:**
```sql
-- ❌ 불필요한 데이터까지 조회
SELECT o.*, u.*, p.*
FROM orders o
JOIN users u ON o.user_id = u.id
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id;
```

**개선:**
```sql
-- ✅ 필요한 컬럼만 조회 (Covering Index 활용 가능)
SELECT o.id, o.total_amount, u.name, p.name
FROM orders o
JOIN users u ON o.user_id = u.id
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id;
```

---

#### 4.3. GROUP BY / ORDER BY 최적화

**문제:**
```sql
-- ❌ 인덱스 없이 GROUP BY (Using temporary, Using filesort 발생)
EXPLAIN SELECT category, COUNT(*)
FROM products
GROUP BY category
ORDER BY COUNT(*) DESC;
```

**해결:**
```sql
-- ✅ GROUP BY 컬럼에 인덱스 추가
CREATE INDEX idx_category ON products(category);

-- EXPLAIN 결과 개선
-- Extra: Using index (Using temporary 제거)
```

---

#### 4.4. 인기 상품 조회 최적화 (실전 예시)

**요구사항:**
- 최근 3일간 판매량 기준 Top 5 상품 조회
- 실시간 순위 제공

**문제가 되는 쿼리:**
```sql
SELECT p.id, p.name, SUM(oi.quantity) AS sales_count
FROM products p
JOIN order_items oi ON p.id = oi.product_id
JOIN orders o ON oi.order_id = o.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
  AND o.status = 'COMPLETED'
GROUP BY p.id, p.name
ORDER BY sales_count DESC
LIMIT 5;
```

**EXPLAIN 분석:**
```
type: ALL (전체 테이블 스캔)
Extra: Using where; Using temporary; Using filesort
```

**최적화 방안 1: 복합 인덱스 추가**
```sql
-- orders 테이블에 복합 인덱스 추가
CREATE INDEX idx_created_status ON orders(created_at, status);

-- order_items 테이블에 인덱스 추가
CREATE INDEX idx_order_product ON order_items(order_id, product_id, quantity);
```

**최적화 방안 2: 비정규화 (집계 테이블)**
```sql
-- 인기 상품 집계 테이블 생성
CREATE TABLE popular_products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sales_count INT NOT NULL,
    period VARCHAR(20) NOT NULL,  -- '3days', '7days', '30days'
    calculated_at DATETIME NOT NULL,
    INDEX idx_period (period, sales_count DESC)
);

-- 배치 작업으로 5분마다 집계 (Scheduled Task)
INSERT INTO popular_products (product_id, sales_count, period, calculated_at)
SELECT p.id, SUM(oi.quantity), '3days', NOW()
FROM products p
JOIN order_items oi ON p.id = oi.product_id
JOIN orders o ON oi.order_id = o.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
  AND o.status = 'COMPLETED'
GROUP BY p.id;

-- 조회는 매우 빠름
SELECT * FROM popular_products
WHERE period = '3days'
ORDER BY sales_count DESC
LIMIT 5;
```

**최적화 결과 비교:**

| 방법 | 응답 시간 | 장점 | 단점 |
|------|----------|------|------|
| 원본 쿼리 | 500ms | 실시간 데이터 | 매우 느림 |
| 인덱스 추가 | 50ms | 구현 간단 | 여전히 JOIN 필요 |
| 비정규화 | 5ms | 매우 빠름 | 배치 작업 필요, 저장 공간 증가 |

---

### 5. 최적화 보고서 작성 가이드

#### 보고서 구조

```markdown
# DB 최적화 보고서

## 1. 문제 정의
- 어떤 기능이 느린가?
- 비즈니스 영향은?
- 성능 목표는?

## 2. 원인 분석
- 쿼리 실행 계획 (EXPLAIN)
- 병목 지점 식별
- 데이터 규모 및 증가 추이

## 3. 해결 방안
- 인덱스 설계
- 쿼리 재구성
- 비정규화 고려
- 대안 비교 (장단점)

## 4. 최적화 결과
- 성능 개선 수치
- 트레이드오프 분석
- 모니터링 계획

## 5. 결론
- 최종 선택 방안
- 향후 개선 과제
```

---

#### 보고서 예시 (인기 상품 조회 최적화)

```markdown
# DB 최적화 보고서: 인기 상품 조회 기능

## 1. 문제 정의

### 대상 기능
- **API**: `GET /products/top?period=3days`
- **요구사항**: 최근 3일간 판매량 기준 Top 5 상품 실시간 조회
- **현재 성능**: 평균 응답 시간 **500ms** (목표: 100ms 이내)

### 비즈니스 영향
- 메인 페이지에서 호출되는 핵심 API
- 일 평균 100만 건 호출
- 응답 지연으로 사용자 이탈 가능성

---

## 2. 원인 분석

### 2.1. 현재 쿼리

\`\`\`sql
SELECT p.id, p.name, SUM(oi.quantity) AS sales_count
FROM products p
JOIN order_items oi ON p.id = oi.product_id
JOIN orders o ON oi.order_id = o.id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
  AND o.status = 'COMPLETED'
GROUP BY p.id, p.name
ORDER BY sales_count DESC
LIMIT 5;
\`\`\`

### 2.2. EXPLAIN 분석

\`\`\`
+----+-------------+-------+------+---------------+------+---------+------+-------+----------+-----------------------------+
| id | select_type | table | type | possible_keys | key  | key_len | ref  | rows  | filtered | Extra                       |
+----+-------------+-------+------+---------------+------+---------+------+-------+----------+-----------------------------+
|  1 | SIMPLE      | o     | ALL  | NULL          | NULL | NULL    | NULL | 50000 |    33.33 | Using where; Using temporary|
|  1 | SIMPLE      | oi    | ALL  | NULL          | NULL | NULL    | NULL | 80000 |    10.00 | Using where; Using filesort |
|  1 | SIMPLE      | p     | ref  | PRIMARY       | id   | 8       | oi.product_id | 1 | 100.00 | NULL                 |
+----+-------------+-------+------+---------------+------+---------+------+-------+----------+-----------------------------+
\`\`\`

**문제점:**
1. `orders` 테이블 전체 스캔 (type: ALL, rows: 50000)
2. `order_items` 테이블 전체 스캔 (type: ALL, rows: 80000)
3. Using temporary, Using filesort 발생 (정렬 비용 큼)

---

## 3. 해결 방안

### 방안 1: 복합 인덱스 추가

\`\`\`sql
-- orders 테이블
CREATE INDEX idx_created_status ON orders(created_at, status);

-- order_items 테이블
CREATE INDEX idx_order_product_qty ON order_items(order_id, product_id, quantity);
\`\`\`

**예상 효과:**
- `orders` 테이블 스캔 행 수 감소: 50000 → 5000
- 응답 시간: 500ms → 50ms

**장점:**
- 구현 간단
- 실시간 데이터 유지

**단점:**
- 여전히 매번 JOIN 필요
- 데이터 증가 시 성능 저하 우려

---

### 방안 2: 비정규화 (집계 테이블)

\`\`\`sql
CREATE TABLE popular_products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sales_count INT NOT NULL,
    period VARCHAR(20) NOT NULL,
    calculated_at DATETIME NOT NULL,
    INDEX idx_period_sales (period, sales_count DESC)
);
\`\`\`

**배치 작업:** 5분마다 집계 업데이트

**예상 효과:**
- 응답 시간: 500ms → 5ms

**장점:**
- 매우 빠른 응답 속도
- 데이터 증가에 영향 없음

**단점:**
- 최대 5분의 데이터 지연
- 저장 공간 추가 필요
- 배치 작업 구현 필요

---

### 방안 3: 캐싱 (Redis)

\`\`\`java
@Cacheable(value = "popularProducts", key = "#period")
public List<PopularProductResponse> getTopProducts(String period) {
    // 기존 쿼리 실행
}
\`\`\`

**TTL:** 5분

**예상 효과:**
- 첫 요청: 500ms
- 이후 요청: 10ms (캐시 히트)

**장점:**
- 구현 간단
- 실시간에 가까운 데이터

**단점:**
- 캐시 워밍업 필요
- Redis 인프라 추가

---

### 방안 비교

| 방안 | 응답 시간 | 데이터 신선도 | 구현 복잡도 | 저장 공간 | 추천 |
|------|----------|--------------|------------|----------|------|
| 인덱스 추가 | 50ms | 실시간 | 낮음 | 작음 | ⭐⭐⭐ |
| 비정규화 | 5ms | 5분 지연 | 높음 | 중간 | ⭐⭐⭐⭐⭐ |
| 캐싱 | 10ms | 5분 지연 | 중간 | 작음 | ⭐⭐⭐⭐ |

---

## 4. 최적화 결과

### 선택 방안: 비정규화 (집계 테이블) + 인덱스 추가

**Phase 1: 인덱스 추가 (단기)**
- 복합 인덱스 추가
- 응답 시간: 500ms → 50ms
- 배포 즉시 적용 가능

**Phase 2: 비정규화 (중기)**
- 집계 테이블 생성
- 배치 작업 구현 (Spring Scheduled)
- 응답 시간: 50ms → 5ms

### 성능 개선 수치

| 항목 | 개선 전 | Phase 1 | Phase 2 |
|------|---------|---------|---------|
| 응답 시간 | 500ms | 50ms | 5ms |
| 스캔 행 수 | 130,000 | 5,000 | 5 |
| 데이터 신선도 | 실시간 | 실시간 | 5분 지연 |

### 트레이드오프 분석

**비용:**
- 저장 공간: +10MB (집계 테이블)
- 배치 작업: CPU 5%, 5분마다
- 개발 시간: 2일

**효과:**
- 응답 시간 **99% 개선** (500ms → 5ms)
- 서버 부하 **95% 감소**
- 사용자 경험 대폭 개선

---

## 5. 결론

### 최종 선택
- **Phase 1 (인덱스 추가)**: 즉시 적용
- **Phase 2 (비정규화)**: 2주 내 적용

### 모니터링 계획
- 응답 시간 모니터링 (목표: 95 percentile 10ms 이내)
- 배치 작업 실행 시간 모니터링
- 집계 데이터 정확성 검증

### 향후 개선 과제
- 데이터 증가 추이 모니터링 (월 100만 건 → 월 1000만 건 대비)
- 캐싱 도입 검토 (Redis)
- 파티셔닝 고려 (주문 데이터 1년 이상 누적 시)
\`\`\`

---

## ✅ 체크리스트

### 성능 병목 식별
- [ ] 조회 성능 저하 가능성이 있는 기능 식별
- [ ] 비즈니스 요구사항과 연결하여 분석
- [ ] 대용량 데이터 환경을 가정

### 쿼리 실행계획 분석
- [ ] `EXPLAIN` 또는 `EXPLAIN ANALYZE` 실행
- [ ] type, key, rows, Extra 컬럼 분석
- [ ] 병목 지점 명확히 파악

### 최적화 방안 도출
- [ ] 인덱스 설계 (Single, Composite, Covering)
- [ ] 쿼리 재구성 (JOIN, WHERE, ORDER BY)
- [ ] 대안 비교 (장단점 분석)

### 최적화 보고서 작성
- [ ] 문제 정의 → 원인 분석 → 해결 방안 → 결과 순서
- [ ] 코드, 쿼리, 실행 계획 포함
- [ ] 정량적 성능 개선 수치 포함
- [ ] 트레이드오프 분석 포함

---

## 🚨 주의사항

### 1. 인덱스는 적절히

**인덱스의 비용:**
- 저장 공간 증가
- INSERT/UPDATE/DELETE 성능 저하
- 인덱스 재구성 비용

**권장:**
- 테이블당 3-5개
- 최대 7-8개를 넘지 않도록
- 사용 빈도 모니터링하여 조정

---

### 2. 비정규화는 신중히

**비정규화 결정 프로세스:**
1. 성능 측정 (현재 얼마나 느린가?)
2. 병목 지점 파악 (왜 느린가?)
3. 대안 검토 (인덱스, 쿼리 최적화, 캐싱)
4. 비정규화 고려 (대안으로 불충분한 경우)

**비정규화가 필요한 경우:**
- 과도한 JOIN (5개 이상)
- 복잡한 집계 연산 (GROUP BY, SUM)
- 대용량 테이블 스캔

---

### 3. 운영 중인 DB 스키마 변경

**인덱스 추가 시 주의:**
```sql
-- ❌ 나쁨: 테이블 락 발생 (MySQL 5.6 이전)
CREATE INDEX idx_category ON products(category);

-- ✅ 좋음: ALGORITHM=INPLACE 사용 (MySQL 5.6+)
CREATE INDEX idx_category ON products(category) ALGORITHM=INPLACE, LOCK=NONE;
```

**안전한 마이그레이션:**
1. 백업 완료
2. 롤백 계획 수립
3. 스테이징 환경에서 테스트
4. 서비스 영향 최소화 (새벽 시간대)
5. 모니터링 준비

---

### 4. 테스트 데이터 준비

**대용량 데이터 생성:**
```sql
-- 상품 10만 건 생성
INSERT INTO products (name, description, price, stock, category)
SELECT
    CONCAT('상품', seq),
    '설명',
    FLOOR(RAND() * 1000000),
    FLOOR(RAND() * 100),
    CASE FLOOR(RAND() * 3)
        WHEN 0 THEN '전자제품'
        WHEN 1 THEN '주변기기'
        ELSE '기타'
    END
FROM (
    SELECT (@ROW := @ROW + 1) AS seq
    FROM information_schema.TABLES t1,
         information_schema.TABLES t2,
         (SELECT @ROW := 0) r
    LIMIT 100000
) x;
```

---

## 🛠️ 유용한 도구 및 기법

### 1. Percona Toolkit

Percona Toolkit은 MySQL/MariaDB 성능 분석 및 최적화를 위한 명령줄 도구 모음입니다.

#### pt-duplicate-key-checker

**중복 인덱스 찾기:**

```bash
# 설치 (Ubuntu)
sudo apt-get install percona-toolkit

# 중복 인덱스 체크
pt-duplicate-key-checker --host=localhost --user=root --password=your_password

# 출력 예시
# ####################################################################
# ecommerce.products
# ####################################################################
#
# idx_category is a duplicate of idx_category_price
# Key definitions:
#   KEY `idx_category` (`category`),
#   KEY `idx_category_price` (`category`,`price`),
#
# 권장: idx_category 제거 (idx_category_price가 포함함)
```

**장점:**
- ✅ 불필요한 인덱스 자동 탐지
- ✅ 중복 인덱스 제거로 쓰기 성능 개선
- ✅ 저장 공간 절약

**공식 문서**: [Percona Toolkit](https://www.percona.com/doc/percona-toolkit/LATEST/index.html)

---

#### pt-query-digest

**슬로우 쿼리 분석:**

```bash
# 슬로우 쿼리 로그 활성화 (MySQL)
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 0.5;  # 0.5초 이상

# 슬로우 쿼리 로그 분석
pt-query-digest /var/log/mysql/slow.log

# 출력 예시
# Query 1: 150 QPS, 0.5s latency, ID 0xA1B2C3D4
# This query is executed 150 times per second
#
# SELECT * FROM products WHERE category = 'electronics' ORDER BY price
#
# 개선 방안: idx_category_price 인덱스 추가
```

**장점:**
- ✅ 가장 느린 쿼리 식별
- ✅ 실행 빈도 및 총 실행 시간 분석
- ✅ 최적화 우선순위 결정

---

### 2. EXPLAIN ANALYZE (MySQL 8.0.18+)

**실제 실행 시간 측정:**

```sql
-- EXPLAIN: 실행 계획만 확인 (실제 실행 안 함)
EXPLAIN SELECT * FROM products WHERE category = '전자제품';

-- EXPLAIN ANALYZE: 실제 실행하여 시간 측정 (권장)
EXPLAIN ANALYZE SELECT * FROM products WHERE category = '전자제품';
```

**출력 예시:**

```
-> Filter: (products.category = '전자제품')  (cost=10.5 rows=100) (actual time=0.05..1.2 rows=98 loops=1)
    -> Table scan on products  (cost=10.5 rows=1000) (actual time=0.04..1.0 rows=1000 loops=1)
```

**해석:**
- `cost=10.5`: 예상 비용
- `rows=100`: 예상 행 수
- `actual time=0.05..1.2`: **실제 실행 시간** (중요!)
- `rows=98`: 실제 반환된 행 수

**장점:**
- ✅ 실제 실행 시간 확인 (추정이 아닌 실측)
- ✅ 예상과 실제의 차이 확인

---

### 3. Explain Visualizer

**실행 계획 시각화:**

- [Explain Visualizer (PostgreSQL)](https://explain.depesz.com/)
- [MySQL Workbench](https://www.mysql.com/products/workbench/) - Visual Explain 기능

**MySQL Workbench 사용법:**

1. 쿼리 작성
2. "Execution Plan" 탭 클릭
3. 시각적으로 실행 계획 확인

**장점:**
- ✅ 복잡한 쿼리의 실행 계획을 시각적으로 이해
- ✅ 병목 지점 쉽게 파악

---

### 4. N+1 문제 탐지 방법

#### 방법 1: p6spy로 쿼리 개수 확인

```gradle
dependencies {
    implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.0'
}
```

**로그 확인:**

```
Hibernate: SELECT * FROM orders WHERE user_id = ?
Hibernate: SELECT * FROM order_items WHERE order_id = 1
Hibernate: SELECT * FROM order_items WHERE order_id = 2
Hibernate: SELECT * FROM order_items WHERE order_id = 3
...
```

**문제점**: 1개의 주문 조회 쿼리 + N개의 주문 상품 조회 쿼리 = **N+1 문제**

---

#### 방법 2: 테스트 코드로 검증

```java
@Test
@DisplayName("N+1 문제 검증")
void N플러스1_문제_검증() {
    // Given
    // 10개의 주문 생성
    for (int i = 0; i < 10; i++) {
        orderRepository.save(Order.create(...));
    }

    // When
    List<Order> orders = orderRepository.findAll();

    // Then: N+1 문제가 있으면 쿼리가 11번 실행됨 (1 + 10)
    // Hibernate 쿼리 카운트 확인 (QueryCountAssert 라이브러리 사용)
    assertThat(queries.getSelect()).isEqualTo(1);  // 실패하면 N+1 문제!
}
```

---

### 5. 데카르트 곱 (Cartesian Product) 문제

**❌ 나쁨: 여러 OneToMany 관계를 Fetch Join**

```java
// 데카르트 곱 발생!
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.items " +
       "JOIN FETCH o.payments")
List<Order> findAllWithItemsAndPayments();
```

**문제점:**
- Order 1개, OrderItem 3개, Payment 2개 → **6개의 행** 반환 (3 × 2)
- 데이터 중복 및 성능 저하

---

**✅ 좋음: 분리해서 조회 또는 @EntityGraph**

```java
// 방법 1: 분리 조회
@Query("SELECT o FROM Order o JOIN FETCH o.items")
List<Order> findAllWithItems();

@Query("SELECT o FROM Order o JOIN FETCH o.payments WHERE o.id IN :ids")
List<Order> findAllWithPayments(@Param("ids") List<Long> ids);

// 방법 2: Batch Size 설정 (권장)
spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

---

## 📚 참고 자료

### 필수 참고 자료
- [Database System Concepts](https://www.db-book.com/)
- [Use The Index, Luke!](https://use-the-index-luke.com/) - 인덱스 최적화 가이드
- [High Performance MySQL](https://www.oreilly.com/library/view/high-performance-mysql/9781492080503/)

### 추천 학습 자료
- [SQL Performance Explained](https://sql-performance-explained.com/)
- [Database Internals](https://www.databass.dev/)
- [Real MySQL 8.0 - 백은빈, 이성욱](https://wikibook.co.kr/realmysql8/)

### 공식 문서
- [MySQL EXPLAIN 공식 문서](https://dev.mysql.com/doc/refman/8.0/en/explain.html)
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

### 유용한 도구
- [MySQL Workbench](https://www.mysql.com/products/workbench/) - 실행 계획 시각화
- [DataGrip](https://www.jetbrains.com/datagrip/) - JetBrains DB 도구
- [Percona Toolkit](https://www.percona.com/doc/percona-toolkit/LATEST/index.html) - MySQL 성능 분석
- [pt-query-digest](https://www.percona.com/doc/percona-toolkit/LATEST/pt-query-digest.html) - 슬로우 쿼리 분석
- [pt-duplicate-key-checker](https://www.percona.com/doc/percona-toolkit/LATEST/pt-duplicate-key-checker.html) - 중복 인덱스 탐지
- [Explain Visualizer](https://explain.depesz.com/) - 실행 계획 시각화

### Percona 블로그 (성능 최적화)
- [UUIDs are Popular, but Bad for Performance](https://www.percona.com/blog/uuids-are-popular-but-bad-for-performance-lets-discuss/)
- [Store UUID in an Optimized Way](https://www.percona.com/blog/store-uuid-optimized-way/)

---

## 🎓 성공적인 과제 제출을 위한 팁

1. **실제 데이터로 테스트**: 수천~수만 건의 데이터로 성능 측정
2. **EXPLAIN 꼼꼼히 분석**: type, key, rows, Extra 컬럼 모두 확인
3. **대안 비교**: 최소 2가지 이상의 방안 비교
4. **정량적 수치 제시**: "빨라졌다"가 아니라 "500ms → 50ms"
5. **트레이드오프 분석**: 장점뿐 아니라 단점도 명확히 제시

---

## 🔗 관련 문서

- `@.claude/commands/week4-step7.md`: STEP 7 DB 통합 가이드
- `@docs/week4/step8-optimization-report-template.md`: 보고서 템플릿
- `@.claude/commands/architecture.md`: Repository 패턴 참조
