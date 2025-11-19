# 성능 측정 및 최적화 가이드 (Performance Optimization)

> **목적**: 동시성 제어를 적용한 시스템의 성능을 측정하고, 병목 지점을 식별하여 최적화하는 방법을 제공한다.

---

## 📌 목차

1. [성능 측정 지표](#1-성능-측정-지표)
2. [병목 지점 식별](#2-병목-지점-식별)
3. [트랜잭션 최적화](#3-트랜잭션-최적화)
4. [인덱스 최적화](#4-인덱스-최적화)
5. [커넥션 풀 튜닝](#5-커넥션-풀-튜닝)
6. [캐싱 전략](#6-캐싱-전략)

---

## 1. 성능 측정 지표

### 📊 핵심 지표

| 지표 | 설명 | 목표 |
|------|------|------|
| **TPS** (Transactions Per Second) | 초당 처리 트랜잭션 수 | 1000+ |
| **응답 시간 (P50)** | 50% 요청의 응답 시간 | <100ms |
| **응답 시간 (P95)** | 95% 요청의 응답 시간 | <300ms |
| **응답 시간 (P99)** | 99% 요청의 응답 시간 | <500ms |
| **에러율** | 전체 요청 중 실패 비율 | <1% |

### 📈 Micrometer를 활용한 메트릭 수집

```java
@Component
@RequiredArgsConstructor
public class PerformanceMetrics {

    private final MeterRegistry registry;

    public void recordStockDecrease(long duration, boolean success) {
        Timer.builder("stock.decrease")
            .tag("status", success ? "success" : "failure")
            .register(registry)
            .record(duration, TimeUnit.MILLISECONDS);
    }

    public void recordCouponIssuance(boolean success) {
        Counter.builder("coupon.issuance")
            .tag("status", success ? "success" : "failure")
            .register(registry)
            .increment();
    }

    public void recordPayment(long amount, boolean success) {
        Counter.builder("payment.total")
            .tag("status", success ? "success" : "failure")
            .register(registry)
            .increment();

        if (success) {
            DistributionSummary.builder("payment.amount")
                .register(registry)
                .record(amount);
        }
    }
}
```

### 🎯 UseCase에 메트릭 적용

```java
@Service
@RequiredArgsConstructor
public class StockUseCase {

    private final ProductRepository productRepository;
    private final PerformanceMetrics metrics;

    @Transactional
    public int decreaseStock(Long productId, int quantity) {
        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

            product.decreaseStock(quantity);
            success = true;

            return product.getStock();

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordStockDecrease(duration, success);
        }
    }
}
```

---

## 2. 병목 지점 식별

### 🔍 Slow Query 로깅

```yaml
# application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.hibernate.orm.jdbc.bind: TRACE
```

### 🧪 Lock 대기 시간 모니터링

```sql
-- MySQL에서 Lock 대기 중인 트랜잭션 확인
SELECT
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query
FROM information_schema.INNODB_LOCK_WAITS w
INNER JOIN information_schema.INNODB_TRX b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.INNODB_TRX r ON r.trx_id = w.requesting_trx_id;

-- 장시간 실행 중인 트랜잭션 찾기
SELECT
    trx_id,
    trx_state,
    trx_started,
    TIME_TO_SEC(TIMEDIFF(NOW(), trx_started)) AS duration_seconds,
    trx_query
FROM information_schema.INNODB_TRX
WHERE TIME_TO_SEC(TIMEDIFF(NOW(), trx_started)) > 5
ORDER BY duration_seconds DESC;
```

### 📊 APM 도구 활용

**권장 도구:**
- Datadog APM
- New Relic
- Pinpoint
- Spring Boot Actuator + Prometheus + Grafana

---

## 3. 트랜잭션 최적화

### ⚡ 트랜잭션 크기 최소화

```java
// ❌ 나쁜 예: 불필요한 작업을 트랜잭션 내에서 수행
@Transactional
public void processOrderBad(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId)
        .orElseThrow();

    // Lock 보유 시간 증가!
    externalService.notifyPartner(order);  // 5초 소요
    sendEmail(order);  // 3초 소요

    order.markAsProcessed();
}

// ✅ 좋은 예: 트랜잭션 외부에서 처리
public void processOrderGood(Long orderId) {
    // 트랜잭션: Lock 보유 시간 최소화
    updateOrderStatus(orderId);

    // 트랜잭션 외부: 외부 API 호출
    Order order = orderRepository.findById(orderId).orElseThrow();
    externalService.notifyPartner(order);
    sendEmail(order);
}

@Transactional
protected void updateOrderStatus(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId)
        .orElseThrow();
    order.markAsProcessed();
}
```

### 🎯 Propagation 전략

```java
@Service
public class OrderService {

    // 기본: REQUIRED (부모 트랜잭션에 참여)
    @Transactional
    public void createOrder(OrderRequest request) {
        // ...
        decreaseStock(request.getProductId(), request.getQuantity());
    }

    // 별도 트랜잭션: REQUIRES_NEW (재고 실패해도 주문은 PENDING으로 저장)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOrderAsPending(OrderRequest request) {
        // ...
    }

    // 읽기 전용: READ_ONLY (성능 최적화)
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }
}
```

### ⏰ 타임아웃 설정

```java
@Transactional(timeout = 3)  // 3초 타임아웃
public void processPayment(PaymentRequest request) {
    // 3초 이상 소요 시 롤백
}
```

---

## 4. 인덱스 최적화

### 📋 인덱스 추가 전략

```sql
-- 1. 재고 차감 시 사용되는 인덱스
CREATE INDEX idx_product_stock ON products(stock) WHERE stock > 0;

-- 2. 쿠폰 중복 발급 방지
CREATE UNIQUE INDEX uk_user_coupon ON user_coupons(user_id, coupon_id);

-- 3. 결제 멱등성 키
CREATE UNIQUE INDEX uk_idempotency ON payments(idempotency_key);

-- 4. 주문 상태별 조회
CREATE INDEX idx_order_status ON orders(status, created_at);

-- 5. 사용자별 쿠폰 조회
CREATE INDEX idx_user_coupon_status ON user_coupons(user_id, status);

-- 6. 복합 인덱스 (커버링 인덱스)
CREATE INDEX idx_order_user_status ON orders(user_id, status) INCLUDE (total_amount, created_at);
```

### 🔍 인덱스 효과 측정

```sql
-- EXPLAIN으로 실행 계획 확인
EXPLAIN
SELECT * FROM products
WHERE id = 1
FOR UPDATE;

-- 인덱스 사용 통계
SELECT
    table_name,
    index_name,
    cardinality,
    seq_in_index
FROM information_schema.STATISTICS
WHERE table_schema = 'ecommerce'
ORDER BY table_name, index_name, seq_in_index;
```

### ⚠️ 인덱스 주의사항

```sql
-- ❌ 나쁜 예: 함수 사용으로 인덱스 미사용
SELECT * FROM orders
WHERE DATE(created_at) = '2025-11-18';

-- ✅ 좋은 예: 범위 검색으로 인덱스 사용
SELECT * FROM orders
WHERE created_at >= '2025-11-18 00:00:00'
  AND created_at < '2025-11-19 00:00:00';
```

### 💡 전문가 의견: 커버링 인덱스 (Covering Index)

#### 율무 코치 (멘토링, DBA 경험)
> "커버링 인덱스는 인덱스만 보고 쿼리 결과를 얻을 수 있으면 커버링 인덱스가 됩니다. SELECT *을 안 하고 일부 컬럼만 조회할 때 고려해볼 수 있습니다."

#### 박트래픽 (성능 전문가, 15년차)
> "커버링 인덱스를 사용하면 테이블에 접근하지 않아도 되기 때문에 디스크 I/O가 획기적으로 줄어듭니다. 성능이 3~10배 향상될 수 있습니다."

#### 커버링 인덱스란?

**일반 인덱스 vs 커버링 인덱스:**
```
일반 인덱스:
1. 인덱스 탐색 → WHERE 조건 찾음
2. 인덱스에서 Primary Key 확인
3. Primary Key로 테이블 접근 (Random I/O)
4. 테이블에서 SELECT 컬럼 읽음

커버링 인덱스:
1. 인덱스 탐색 → WHERE 조건 찾음
2. 인덱스에 SELECT 컬럼도 모두 있음!
3. 테이블 접근 없이 인덱스만 읽고 끝 (Sequential I/O)
```

#### 김데이터 (DBA, 20년차)
> "InnoDB에서 Secondary Index는 항상 Primary Key를 포함합니다. 따라서 SELECT 절에 인덱스 컬럼 + PK만 있으면 커버링 인덱스가 됩니다."

**실무 예시:**

```sql
-- 테이블 구조
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    product_id BIGINT,
    quantity INT,
    total_amount INT,
    status VARCHAR(20),
    created_at TIMESTAMP
);

-- 자주 실행하는 쿼리
SELECT user_id, total_amount, created_at
FROM orders
WHERE status = 'PAID'
  AND created_at >= '2025-11-01';
```

**❌ 커버링 인덱스 없는 경우**
```sql
-- 인덱스: (status, created_at)
CREATE INDEX idx_status_created ON orders(status, created_at);

-- 쿼리 실행 과정:
-- 1. 인덱스 탐색: status='PAID' AND created_at >= '2025-11-01' 조건 찾음
-- 2. 인덱스에서 Primary Key (id) 확인
-- 3. 📖 실제 테이블로 가서 user_id, total_amount 읽음 (느림!)

-- EXPLAIN 결과:
-- type: ref
-- Extra: Using index condition (테이블 접근 O)
```

**✅ 커버링 인덱스 있는 경우**
```sql
-- 커버링 인덱스: 쿼리에 필요한 모든 컬럼 포함
CREATE INDEX idx_covering ON orders(status, created_at, user_id, total_amount);

-- 쿼리 실행 과정:
-- 1. 인덱스 탐색: status='PAID' AND created_at >= '2025-11-01' 조건 찾음
-- 2. 인덱스에 user_id, total_amount도 있음!
-- 3. ✅ 테이블 안 가고 인덱스만 읽고 끝! (빠름!)

-- EXPLAIN 결과:
-- type: ref
-- Extra: Using index (테이블 접근 X)
```

**성능 비교:**

| 방식 | 디스크 I/O | 속도 | 메모리 사용 |
|------|-----------|------|------------|
| 일반 쿼리 | 많음 (테이블 접근) | 느림 | 많음 |
| 커버링 인덱스 | 적음 (인덱스만) | **빠름 (3~10배)** | 적음 |

#### 최아키텍트 (MSA, 10년차)
> "API 응답 성능을 최적화할 때 가장 먼저 하는 작업이 커버링 인덱스 적용입니다. SELECT * 대신 필요한 컬럼만 선택하고 인덱스를 설계하세요."

**JPA에서 커버링 인덱스 활용:**

```java
// ❌ 나쁜 예: SELECT * (커버링 인덱스 불가능)
@Query("SELECT o FROM Order o WHERE o.status = :status")
List<Order> findByStatus(@Param("status") String status);

// ✅ 좋은 예: 필요한 컬럼만 (커버링 인덱스 가능)
@Query("SELECT new com.example.dto.OrderSummary(o.userId, o.totalAmount, o.createdAt) " +
       "FROM Order o WHERE o.status = :status")
List<OrderSummary> findSummaryByStatus(@Param("status") String status);

// DTO
@Getter
@AllArgsConstructor
public class OrderSummary {
    private Long userId;
    private Integer totalAmount;
    private Instant createdAt;
}

// 인덱스
// CREATE INDEX idx_covering ON orders(status, user_id, total_amount, created_at);
```

### 💡 전문가 의견: 인덱스 풀 스캔 (Index Full Scan)

#### 율무 코치 (멘토링, DBA 경험)
> "인덱스를 활용하긴 하는데 인덱스 범위 안에 있는 컬럼들을 거의 다 스캔하고 있으면 성능이 더 안 나올 수 있습니다."

#### 김데이터 (DBA, 20년차)
> "인덱스를 만들었다고 무조건 빠른 게 아닙니다. 인덱스 풀 스캔이 발생하면 오히려 테이블 풀 스캔보다 느릴 수 있습니다."

**인덱스 스캔 vs 인덱스 풀 스캔:**

```sql
-- 테이블: 100만 건
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    category VARCHAR(50),
    price INT,
    stock INT
);

-- 인덱스 생성
CREATE INDEX idx_category ON products(category);

-- ❌ 인덱스 풀 스캔 발생 (느림)
SELECT * FROM products
WHERE category LIKE '%전자%';  -- 중간 매칭: 인덱스 못 씀
-- → 100만 건 전부 확인

-- ❌ 인덱스 풀 스캔 발생 (느림)
SELECT * FROM products
WHERE category != 'laptop';  -- 부정 조건: 거의 모든 데이터
-- → 100만 건 중 95만 건 확인

-- ✅ 인덱스 범위 스캔 (빠름)
SELECT * FROM products
WHERE category = 'laptop';  -- 정확한 매칭
-- → 5만 건만 확인

-- ✅ 인덱스 범위 스캔 (빠름)
SELECT * FROM products
WHERE category LIKE 'laptop%';  -- 앞부분 매칭
-- → 5만 건만 확인
```

**EXPLAIN으로 확인하기:**

```sql
-- 실행 계획 확인
EXPLAIN SELECT * FROM products WHERE category LIKE '%전자%';

-- 결과
+----+-------------+----------+-------+------+---------+------+--------+-------------+
| id | select_type | table    | type  | key  | key_len | ref  | rows   | Extra       |
+----+-------------+----------+-------+------+---------+------+--------+-------------+
|  1 | SIMPLE      | products | index | idx  | 202     | NULL | 1000000| Using where |
+----+-------------+----------+-------+------+---------+------+--------+-------------+

-- type = 'index' → 인덱스 풀 스캔!
-- rows = 1000000 → 100만 건 전부 확인!
```

#### 박트래픽 (성능 전문가, 15년차)
> "인덱스 풀 스캔이 발생하면 인덱스를 삭제하는 게 나을 수 있습니다. 인덱스는 읽기는 빠르지만 쓰기(INSERT, UPDATE, DELETE)를 느리게 만들기 때문입니다."

**해결 방법:**

```sql
-- 1. Full-Text Search 사용 (중간 매칭이 필요한 경우)
CREATE FULLTEXT INDEX idx_fulltext ON products(category);

SELECT * FROM products
WHERE MATCH(category) AGAINST('전자' IN BOOLEAN MODE);

-- 2. 조건 변경 (부정 → 긍정)
-- ❌
WHERE category != 'laptop'

-- ✅
WHERE category IN ('smartphone', 'tablet', 'desktop', ...)

-- 3. 복합 인덱스 활용
CREATE INDEX idx_category_price ON products(category, price);

SELECT * FROM products
WHERE category = 'laptop'
  AND price BETWEEN 1000000 AND 2000000;
```

#### 정스타트업 (CTO, 7년차)
> "초기에는 인덱스를 많이 만들었는데, 나중에 보니 절반 이상이 사용되지 않거나 풀 스캔만 발생하는 인덱스였습니다. 주기적으로 인덱스 사용률을 모니터링하세요."

**인덱스 사용률 모니터링:**

```sql
-- MySQL: 인덱스 사용 통계
SELECT
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,
    STAT_VALUE AS 'Rows Read'
FROM information_schema.STATISTICS s
LEFT JOIN information_schema.INNODB_INDEX_STATS i
    ON s.TABLE_NAME = i.TABLE_NAME
    AND s.INDEX_NAME = i.INDEX_NAME
WHERE s.TABLE_SCHEMA = 'ecommerce'
ORDER BY STAT_VALUE DESC;

-- 사용되지 않는 인덱스 찾기 (Performance Schema 필요)
SELECT
    object_schema,
    object_name,
    index_name
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE index_name IS NOT NULL
  AND count_star = 0
  AND object_schema = 'ecommerce'
ORDER BY object_schema, object_name;
```

---

## 5. 커넥션 풀 튜닝

### 📊 HikariCP 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 최대 커넥션 수
      minimum-idle: 10  # 최소 유휴 커넥션
      connection-timeout: 3000  # 커넥션 획득 타임아웃 (3초)
      idle-timeout: 600000  # 유휴 커넥션 타임아웃 (10분)
      max-lifetime: 1800000  # 커넥션 최대 수명 (30분)
      leak-detection-threshold: 2000  # 누수 감지 (2초)
```

### 🎯 적정 Pool Size 계산

```
공식: pool_size = Tn × (Cm - 1) + 1

Tn: 동시 스레드 수
Cm: 각 스레드의 평균 동시 커넥션 수

예시:
- 동시 요청: 100개
- 각 요청당 커넥션: 1개
→ Pool Size = 100 × (1 - 1) + 1 = 1 (최소)

실무 권장:
- 동시 요청: 100개
- 여유분: 2배
→ Pool Size = 100 × 2 = 200
```

### 📈 커넥션 풀 모니터링

```java
@Component
@RequiredArgsConstructor
public class ConnectionPoolMetrics {

    private final DataSource dataSource;
    private final MeterRegistry registry;

    @Scheduled(fixedDelay = 5000)
    public void recordPoolMetrics() {
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();

            registry.gauge("hikari.pool.total", pool, HikariPoolMXBean::getTotalConnections);
            registry.gauge("hikari.pool.active", pool, HikariPoolMXBean::getActiveConnections);
            registry.gauge("hikari.pool.idle", pool, HikariPoolMXBean::getIdleConnections);
            registry.gauge("hikari.pool.waiting", pool, HikariPoolMXBean::getThreadsAwaitingConnection);
        }
    }
}
```

---

## 6. 캐싱 전략

### 🚀 Redis 캐싱

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * 상품 조회 (캐시 적용)
     */
    public Product getProduct(Long productId) {
        String cacheKey = "product:" + productId;

        // 1. 캐시 조회
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return deserialize(cached, Product.class);
        }

        // 2. DB 조회
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // 3. 캐시 저장 (TTL 5분)
        redisTemplate.opsForValue().set(
            cacheKey,
            serialize(product),
            Duration.ofMinutes(5)
        );

        return product;
    }

    /**
     * 상품 업데이트 (캐시 무효화)
     */
    @Transactional
    public void updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
            .orElseThrow();

        product.update(request);

        // 캐시 무효화
        redisTemplate.delete("product:" + productId);
    }
}
```

### 🎯 캐시 전략 선택

| 데이터 유형 | 캐시 전략 | TTL | 무효화 |
|------------|----------|-----|--------|
| **상품 정보** | Look-Aside | 5분 | 업데이트 시 삭제 |
| **재고** | Write-Through | 1분 | 차감 시 즉시 업데이트 |
| **쿠폰 수량** | Write-Through | - | 발급 시 즉시 차감 |
| **사용자 잔액** | Write-Through | - | 충전/차감 시 즉시 업데이트 |
| **인기 상품** | Batch Update | 10분 | 배치 스케줄러 |

---

## 7. 부하 테스트

### 🔥 JMeter 테스트 플랜

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan>
      <stringProp name="TestPlan.comments">E-Commerce 부하 테스트</stringProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
    </TestPlan>

    <hashTree>
      <ThreadGroup>
        <stringProp name="ThreadGroup.num_threads">100</stringProp>
        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
        <stringProp name="ThreadGroup.duration">60</stringProp>
      </ThreadGroup>

      <HTTPSamplerProxy>
        <stringProp name="HTTPSampler.domain">localhost</stringProp>
        <stringProp name="HTTPSampler.port">8080</stringProp>
        <stringProp name="HTTPSampler.path">/api/products/1/stock/decrease</stringProp>
        <stringProp name="HTTPSampler.method">POST</stringProp>
      </HTTPSamplerProxy>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### 📊 K6 부하 테스트 스크립트

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // 워밍업
    { duration: '1m', target: 100 },   // 부하 증가
    { duration: '2m', target: 500 },   // 피크
    { duration: '1m', target: 100 },   // 하락
    { duration: '30s', target: 0 },    // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],  // 95%가 300ms 이하
    errors: ['rate<0.01'],             // 에러율 1% 이하
  },
};

export default function () {
  const productId = Math.floor(Math.random() * 100) + 1;

  // 재고 차감 요청
  const res = http.post(`http://localhost:8080/api/products/${productId}/stock/decrease`, JSON.stringify({
    quantity: 1
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(1);
}
```

---

## 8. 성능 최적화 체크리스트

### ✅ 트랜잭션
- [ ] 트랜잭션 크기를 최소화했는가?
- [ ] 외부 API 호출을 트랜잭션 밖에서 수행하는가?
- [ ] 타임아웃을 설정했는가?
- [ ] 읽기 전용 트랜잭션에 `readOnly=true`를 사용하는가?

### ✅ Lock
- [ ] Lock 보유 시간을 최소화했는가?
- [ ] Deadlock 방지 로직이 있는가? (정렬된 순서로 락 획득)
- [ ] Lock Timeout을 설정했는가?
- [ ] 충돌이 드문 경우 Optimistic Lock을 고려했는가?

### ✅ 인덱스
- [ ] 모든 Foreign Key에 인덱스가 있는가?
- [ ] WHERE 절에 사용되는 컬럼에 인덱스가 있는가?
- [ ] 복합 인덱스 순서가 적절한가? (선택도 높은 컬럼 우선)
- [ ] EXPLAIN으로 실행 계획을 확인했는가?

### ✅ 커넥션 풀
- [ ] Pool Size가 적절한가? (동시 요청 수 고려)
- [ ] Connection Timeout이 설정되어 있는가?
- [ ] Leak Detection이 활성화되어 있는가?
- [ ] 커넥션 풀 메트릭을 모니터링하는가?

### ✅ 캐싱
- [ ] 읽기가 많은 데이터에 캐싱을 적용했는가?
- [ ] TTL을 적절히 설정했는가?
- [ ] 캐시 무효화 전략이 있는가?
- [ ] Cache Hit Rate를 모니터링하는가?

---

## 📚 참고 자료

- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [MySQL Performance Tuning](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)
- [Redis Best Practices](https://redis.io/docs/manual/patterns/)
- [Spring Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)

---

**작성일**: 2025-11-18
**버전**: 1.0
