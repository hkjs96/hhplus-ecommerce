# Lock 메커니즘과 Race Condition (Lock Mechanisms & Race Conditions)

> **목적**: 데이터베이스 Lock의 종류와 동작 원리를 이해하고, Race Condition을 식별하고 해결하는 방법을 학습한다.

---

## 📌 Lock이란?

**Lock**은 트랜잭션이 데이터에 대한 배타적 접근 권한을 확보하는 메커니즘입니다. 동시성 제어의 핵심 기술입니다.

### Lock의 필요성

```sql
-- Lock 없이 동시 실행 시
-- Transaction A
UPDATE accounts SET balance = balance - 1000 WHERE id = 1;

-- Transaction B (동시 실행)
UPDATE accounts SET balance = balance + 500 WHERE id = 1;

-- 결과: Lost Update 발생 가능
```

**Lock 적용 시:**
```sql
-- Transaction A
BEGIN;
SELECT balance FROM accounts WHERE id = 1 FOR UPDATE;  -- Lock 획득
UPDATE accounts SET balance = balance - 1000 WHERE id = 1;
COMMIT;  -- Lock 해제

-- Transaction B (대기 후 실행)
BEGIN;
SELECT balance FROM accounts WHERE id = 1 FOR UPDATE;  -- A가 끝날 때까지 대기
UPDATE accounts SET balance = balance + 500 WHERE id = 1;
COMMIT;
```

---

## 🔐 Lock의 종류

### 1. Row-Level Lock (행 레벨 락)

**특정 행(Row)에만 Lock을 걸어 다른 트랜잭션의 접근을 제어합니다.**

#### Shared Lock (S-Lock, 읽기 락)

```sql
-- MySQL/PostgreSQL
SELECT * FROM products WHERE id = 1 FOR SHARE;
-- 또는
SELECT * FROM products WHERE id = 1 LOCK IN SHARE MODE;  -- MySQL
```

**특징:**
- 여러 트랜잭션이 동시에 읽기 가능
- 쓰기는 불가능 (X-Lock 획득 불가)

**사용 케이스:**
```sql
-- Transaction A
BEGIN;
SELECT stock FROM products WHERE id = 1 FOR SHARE;
-- stock = 10

-- Transaction B (동시 실행 가능)
SELECT stock FROM products WHERE id = 1 FOR SHARE;
-- stock = 10 (동시 읽기 가능)

-- Transaction C (대기 발생)
UPDATE products SET stock = 5 WHERE id = 1;
-- A와 B가 COMMIT할 때까지 대기
```

---

#### Exclusive Lock (X-Lock, 쓰기 락)

```sql
-- MySQL/PostgreSQL
SELECT * FROM products WHERE id = 1 FOR UPDATE;
```

**특징:**
- 한 트랜잭션만 접근 가능
- 읽기/쓰기 모두 불가능 (다른 트랜잭션은 대기)

**사용 케이스:**
```sql
-- Transaction A
BEGIN;
SELECT stock FROM products WHERE id = 1 FOR UPDATE;
-- Exclusive Lock 획득

-- Transaction B (대기 발생)
SELECT stock FROM products WHERE id = 1 FOR SHARE;
-- A가 COMMIT할 때까지 대기

-- Transaction C (대기 발생)
UPDATE products SET stock = 5 WHERE id = 1;
-- A가 COMMIT할 때까지 대기
```

---

### 2. Lock 호환성 매트릭스

|  | **S-Lock (읽기)** | **X-Lock (쓰기)** |
|---|---|---|
| **S-Lock (읽기)** | ✅ 호환 (동시 읽기 가능) | ❌ 충돌 (대기) |
| **X-Lock (쓰기)** | ❌ 충돌 (대기) | ❌ 충돌 (대기) |

**예시:**
```sql
-- Transaction A: S-Lock 획득
SELECT * FROM products WHERE id = 1 FOR SHARE;

-- Transaction B: S-Lock 획득 가능 (✅ 호환)
SELECT * FROM products WHERE id = 1 FOR SHARE;

-- Transaction C: X-Lock 획득 불가 (❌ 충돌, 대기)
SELECT * FROM products WHERE id = 1 FOR UPDATE;
```

---

### 3. Table-Level Lock (테이블 레벨 락)

#### Intention Lock (의도 락)

**행 레벨 Lock을 걸기 전에 테이블에 먼저 거는 Lock**

```
테이블 Lock 계층:
IS (Intention Shared)
  ↓
IX (Intention Exclusive)
  ↓
S (Shared)
  ↓
X (Exclusive)
```

**동작 방식:**
```sql
-- Transaction A가 행에 X-Lock을 걸 때:
1. 테이블에 IX-Lock 획득
2. 행에 X-Lock 획득

-- 이제 다른 트랜잭션은 테이블 전체에 S-Lock/X-Lock을 걸 수 없음
```

---

### 4. Lock Escalation (락 에스컬레이션)

**행 레벨 Lock이 너무 많아지면 테이블 레벨 Lock으로 승격됩니다.**

```
많은 행 Lock
  ↓
임계값 초과
  ↓
Page Lock (페이지 단위)
  ↓
임계값 초과
  ↓
Table Lock (테이블 전체)
```

**문제점:**
```sql
-- Transaction A: 10만 건 UPDATE
UPDATE products SET category = 'sale';  -- 전체 테이블 Lock 발생

-- Transaction B: 단일 행 조회도 대기
SELECT * FROM products WHERE id = 1 FOR UPDATE;
-- A가 끝날 때까지 대기!
```

**해결책:**
```sql
-- 배치 단위로 나누어 처리
UPDATE products SET category = 'sale' WHERE id BETWEEN 1 AND 1000;
COMMIT;

UPDATE products SET category = 'sale' WHERE id BETWEEN 1001 AND 2000;
COMMIT;

-- 반복...
```

---

## ⚠️ Deadlock (교착 상태)

### Deadlock이란?

**두 개 이상의 트랜잭션이 서로가 가진 Lock을 기다리며 무한 대기하는 상황입니다.**

### Deadlock 시나리오

```
Time    Transaction A                  Transaction B
----    -----------------              -----------------
T1      BEGIN;
        SELECT * FROM products
        WHERE id = 1 FOR UPDATE;
        (Lock A 획득)

T2                                     BEGIN;
                                       SELECT * FROM orders
                                       WHERE id = 100 FOR UPDATE;
                                       (Lock B 획득)

T3      SELECT * FROM orders
        WHERE id = 100 FOR UPDATE;
        (Lock B 대기...)

T4                                     SELECT * FROM products
                                       WHERE id = 1 FOR UPDATE;
                                       (Lock A 대기...)

        🔒 DEADLOCK 발생!
```

**MySQL 동작:**
```
Deadlock 감지
  ↓
Victim 선택 (작업이 적은 트랜잭션)
  ↓
ROLLBACK
  ↓
나머지 트랜잭션 계속 실행
```

---

### Deadlock 확인 방법

#### MySQL

```sql
-- Deadlock 정보 확인
SHOW ENGINE INNODB STATUS\\G

-- 출력 예시:
------------------------
LATEST DETECTED DEADLOCK
------------------------
2025-11-18 22:00:00
*** (1) TRANSACTION:
TRANSACTION 12345, ACTIVE 2 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 2 lock struct(s), heap size 1136, 1 row lock(s)
MySQL thread id 10, OS thread handle 140123456789, query id 100 localhost root updating
UPDATE products SET stock = stock - 1 WHERE id = 1

*** (2) TRANSACTION:
TRANSACTION 12346, ACTIVE 1 sec starting index read
mysql tables in use 1, locked 1
3 lock struct(s), heap size 1136, 2 row lock(s)
MySQL thread id 11, OS thread handle 140123456790, query id 101 localhost root updating
UPDATE orders SET status = 'PAID' WHERE id = 100

*** WE ROLL BACK TRANSACTION (2)
```

#### PostgreSQL

```sql
-- 현재 Lock 대기 중인 쿼리 확인
SELECT
    blocked_locks.pid AS blocked_pid,
    blocked_activity.usename AS blocked_user,
    blocking_locks.pid AS blocking_pid,
    blocking_activity.usename AS blocking_user,
    blocked_activity.query AS blocked_statement,
    blocking_activity.query AS blocking_statement
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

---

### Deadlock 해결 전략

#### 1. Lock 순서 통일 (가장 효과적)

```sql
-- ❌ 나쁜 예: 트랜잭션마다 다른 순서로 Lock 획득
-- Transaction A
UPDATE products WHERE id = 1;  -- Lock 1
UPDATE orders WHERE id = 100;  -- Lock 2

-- Transaction B
UPDATE orders WHERE id = 100;  -- Lock 2
UPDATE products WHERE id = 1;  -- Lock 1
-- → Deadlock 발생!

-- ✅ 좋은 예: 항상 동일한 순서로 Lock 획득
-- Transaction A
UPDATE orders WHERE id = 100;  -- Lock 1 (orders 먼저)
UPDATE products WHERE id = 1;  -- Lock 2

-- Transaction B
UPDATE orders WHERE id = 100;  -- Lock 1 (orders 먼저)
UPDATE products WHERE id = 1;  -- Lock 2
-- → Deadlock 방지!
```

**실무 예시:**
```java
// ✅ ID를 정렬하여 항상 같은 순서로 Lock 획득
public void updateMultipleProducts(List<Long> productIds) {
    // ID 오름차순 정렬
    Collections.sort(productIds);

    for (Long productId : productIds) {
        Product product = em.createQuery(
            "SELECT p FROM Product p WHERE p.id = :id", Product.class)
            .setParameter("id", productId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getSingleResult();

        product.updateStock();
    }
}
```

---

#### 2. Lock Timeout 설정

```sql
-- MySQL: Lock 대기 시간 설정 (초 단위)
SET innodb_lock_wait_timeout = 5;  -- 5초

-- PostgreSQL: Statement timeout (밀리초 단위)
SET statement_timeout = 5000;  -- 5초
```

**애플리케이션에서 재시도:**
```java
@Transactional
public void decreaseStockWithRetry(Long productId, int quantity) {
    int maxRetries = 3;

    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow();

            product.decreaseStock(quantity);
            return;  // 성공

        } catch (PessimisticLockException e) {
            if (attempt == maxRetries - 1) {
                throw new StockUpdateFailedException("재고 업데이트 실패", e);
            }

            // Exponential Backoff
            Thread.sleep(100 * (attempt + 1));
        }
    }
}
```

---

#### 3. 트랜잭션 크기 최소화

```sql
-- ❌ 나쁜 예: 긴 트랜잭션
BEGIN;
UPDATE products SET stock = stock - 1 WHERE id = 1;
-- 복잡한 로직 (10초 소요)
UPDATE orders SET status = 'PAID' WHERE id = 100;
-- 외부 API 호출 (5초 소요)
COMMIT;

-- ✅ 좋은 예: 짧은 트랜잭션
BEGIN;
UPDATE products SET stock = stock - 1 WHERE id = 1;
UPDATE orders SET status = 'PAID' WHERE id = 100;
COMMIT;

-- 외부 API 호출은 트랜잭션 밖에서
```

---

#### 4. 인덱스 추가 (Lock 범위 최소화)

```sql
-- ❌ 인덱스 없이 조회 → Table Scan → 전체 테이블 Lock
UPDATE orders SET status = 'PAID'
WHERE user_id = 123 AND created_at > '2025-11-01';

-- ✅ 인덱스 추가 → 필요한 행만 Lock
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at);

UPDATE orders SET status = 'PAID'
WHERE user_id = 123 AND created_at > '2025-11-01';
```

---

## 🏃 Race Condition 패턴과 해결

### Race Condition이란?

**여러 프로세스/스레드가 공유 자원에 동시에 접근할 때 실행 순서에 따라 결과가 달라지는 상황입니다.**

---

### 1. Lost Update (갱신 손실)

**가장 흔한 Race Condition**

```sql
-- 초기 상태: stock = 10

-- Time    Transaction A              Transaction B
-- T1      SELECT stock FROM products
--         WHERE id = 1;
--         stock = 10

-- T2                                 SELECT stock FROM products
--                                    WHERE id = 1;
--                                    stock = 10

-- T3      new_stock = 10 - 3 = 7

-- T4                                 new_stock = 10 - 5 = 5

-- T5      UPDATE products
--         SET stock = 7
--         WHERE id = 1;

-- T6                                 UPDATE products
--                                    SET stock = 5
--                                    WHERE id = 1;

-- 결과: stock = 5 (A의 차감 손실!)
-- 올바른 결과: stock = 10 - 3 - 5 = 2
```

---

#### 해결 방법 1: 원자적 연산

```sql
-- ✅ 원자적 연산 (Atomic Operation)
UPDATE products
SET stock = stock - 5
WHERE id = 1 AND stock >= 5;

-- affected_rows 확인
IF ROW_COUNT() = 0 THEN
    -- 재고 부족 또는 동시성 문제
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '재고 부족';
END IF;
```

**Java 구현:**
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity " +
           "WHERE p.id = :id AND p.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}

@Service
public class StockService {

    public void decreaseStock(Long productId, int quantity) {
        int updated = productRepository.decreaseStock(productId, quantity);

        if (updated == 0) {
            throw new InsufficientStockException("재고 부족");
        }
    }
}
```

---

#### 해결 방법 2: SELECT FOR UPDATE

```sql
-- ✅ Pessimistic Lock
BEGIN;

SELECT stock FROM products
WHERE id = 1
FOR UPDATE;  -- Exclusive Lock 획득

-- 다른 트랜잭션은 이 행에 접근 불가
IF stock >= 5 THEN
    UPDATE products SET stock = stock - 5 WHERE id = 1;
    COMMIT;
ELSE
    ROLLBACK;
END IF;
```

---

#### 해결 방법 3: 낙관적 락 (Version)

```sql
-- ✅ Optimistic Lock
-- 조회
SELECT id, stock, version FROM products WHERE id = 1;
-- stock = 10, version = 5

-- 업데이트 (version 체크)
UPDATE products
SET stock = stock - 5,
    version = version + 1
WHERE id = 1
  AND version = 5;

-- affected_rows = 0이면 충돌 발생 → 재시도
IF ROW_COUNT() = 0 THEN
    -- 다른 트랜잭션이 먼저 수정함
    -- 재시도 로직
END IF;
```

---

### 2. Dirty Check (더티 체크)

**커밋되지 않은 데이터를 읽어 잘못된 결정을 내리는 경우**

```sql
-- Transaction A
BEGIN;
UPDATE products SET stock = 0 WHERE id = 1;
-- 아직 COMMIT 안 함

-- Transaction B (READ UNCOMMITTED)
SELECT stock FROM products WHERE id = 1;
-- stock = 0 읽음

IF stock = 0 THEN
    -- "품절" 처리
END IF

-- Transaction A
ROLLBACK;  -- 재고 변경 취소됨

-- Transaction B는 잘못된 판단을 했음!
```

**해결 방법:**
```sql
-- READ COMMITTED 이상의 격리 수준 사용
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

---

### 3. Race on Insert (삽입 경쟁)

**중복 데이터 삽입 방지**

```sql
-- 포인트 중복 적립 방지

-- ❌ 문제가 있는 코드
-- Transaction A
SELECT COUNT(*) FROM point_history
WHERE user_id = 1 AND event_id = 'ORDER_001';
-- count = 0

INSERT INTO point_history (user_id, event_id, points)
VALUES (1, 'ORDER_001', 100);

-- Transaction B (동시 실행)
SELECT COUNT(*) FROM point_history
WHERE user_id = 1 AND event_id = 'ORDER_001';
-- count = 0 (A가 아직 COMMIT 안 함)

INSERT INTO point_history (user_id, event_id, points)
VALUES (1, 'ORDER_001', 100);

-- 결과: 중복 적립!
```

**해결 방법 1: Unique Constraint**
```sql
-- ✅ DB 제약조건으로 원천 차단
ALTER TABLE point_history
ADD UNIQUE KEY unique_event (user_id, event_id);

-- 중복 시도 시 에러 발생
INSERT INTO point_history (user_id, event_id, points)
VALUES (1, 'ORDER_001', 100);
-- ERROR 1062: Duplicate entry '1-ORDER_001'
```

**해결 방법 2: INSERT IGNORE**
```sql
-- ✅ 중복 시 무시
INSERT IGNORE INTO point_history (user_id, event_id, points)
VALUES (1, 'ORDER_001', 100);

-- affected_rows = 0이면 이미 존재함
```

**PostgreSQL:**
```sql
-- ✅ ON CONFLICT
INSERT INTO point_history (user_id, event_id, points)
VALUES (1, 'ORDER_001', 100)
ON CONFLICT (user_id, event_id) DO NOTHING;

-- 또는 UPSERT
ON CONFLICT (user_id, event_id) DO UPDATE
SET points = point_history.points + EXCLUDED.points;
```

---

### 4. Double Dispatch (중복 처리)

**동일한 요청이 2번 처리되는 경우**

```sql
-- 결제 중복 처리 방지

-- ❌ 문제가 있는 코드
-- 사용자가 결제 버튼 중복 클릭
-- Request 1
INSERT INTO payments (order_id, user_id, amount)
VALUES (100, 1, 50000);

-- Request 2 (동시 도착)
INSERT INTO payments (order_id, user_id, amount)
VALUES (100, 1, 50000);

-- 결과: 동일 주문에 대해 2번 결제!
```

**해결 방법: Idempotency Key**
```sql
-- ✅ 멱등성 키 사용
CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 중복 요청 시 Unique Constraint 위반
INSERT INTO payments (idempotency_key, order_id, user_id, amount, status)
VALUES ('payment-100-uuid-12345', 100, 1, 50000, 'SUCCESS');

-- 이미 존재하면 에러 발생
-- ERROR 1062: Duplicate entry 'payment-100-uuid-12345'
```

**애플리케이션 구현:**
```java
@Transactional
public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
    // 1. 이미 처리된 요청인지 확인
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        log.info("Duplicate payment request: {}", idempotencyKey);
        return PaymentResult.from(existing.get());
    }

    // 2. 결제 처리
    Payment payment = Payment.create(idempotencyKey, request);
    paymentRepository.save(payment);

    return PaymentResult.success(payment);
}
```

---

## 🎯 실무 해결 전략 요약

| Race Condition | 추천 해결 방법 | 복잡도 | 성능 |
|---------------|--------------|--------|------|
| **Lost Update (재고 차감)** | 원자적 연산 또는 SELECT FOR UPDATE | 낮음 | 높음 |
| **Dirty Check** | READ COMMITTED 이상 | 낮음 | 높음 |
| **Race on Insert (중복 방지)** | Unique Constraint + INSERT IGNORE | 낮음 | 높음 |
| **Double Dispatch (중복 결제)** | Idempotency Key | 중간 | 높음 |
| **좌석 예약** | SELECT FOR UPDATE | 중간 | 중간 |
| **분산 환경 동시성** | Redis Distributed Lock | 높음 | 높음 |

---

## 📚 참고 자료

- [MySQL - InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [PostgreSQL - Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)
- [Wikipedia - Deadlock](https://en.wikipedia.org/wiki/Deadlock)
- [Wikipedia - Race Condition](https://en.wikipedia.org/wiki/Race_condition)

---

**작성일**: 2025-11-18
**버전**: 1.0
