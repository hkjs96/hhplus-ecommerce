# 트랜잭션 기초 개념 (Transaction Fundamentals)

> **목적**: 동시성 제어를 이해하기 위한 트랜잭션의 기본 개념과 ACID 속성, 격리 수준을 학습한다.

---

## 📌 트랜잭션이란?

**트랜잭션(Transaction)**은 데이터베이스의 논리적 작업 단위입니다. 여러 개의 쿼리를 하나의 작업으로 묶어서 **모두 성공하거나 모두 실패**하도록 보장합니다.

### 왜 트랜잭션이 필요한가?

실무에서는 여러 테이블을 동시에 수정해야 하는 경우가 빈번합니다. 트랜잭션이 없다면 일부만 성공하고 일부는 실패하여 **데이터 불일치**가 발생할 수 있습니다.

### 실무 시나리오

| 시나리오 | 필요한 작업 | 트랜잭션 없이 발생할 문제 |
|---------|-----------|----------------------|
| **은행 계좌 이체** | A 계좌 출금 + B 계좌 입금 | A에서만 출금되고 B에 입금 안 됨 |
| **주문 처리** | 재고 차감 + 주문 생성 + 결제 처리 | 재고만 차감되고 주문 미생성 |
| **회원 가입** | 사용자 정보 저장 + 기본 권한 할당 + 웰컴 포인트 지급 | 사용자만 생성되고 권한/포인트 미지급 |
| **게시글 삭제** | 게시글 삭제 + 댓글 삭제 + 첨부파일 삭제 | 게시글만 삭제되고 댓글 남음 |

### 트랜잭션 흐름

```
사용자 요청
    ↓
BEGIN TRANSACTION  ← 트랜잭션 시작
    ↓
작업 1 (INSERT)
    ↓
작업 2 (UPDATE)
    ↓
작업 3 (DELETE)
    ↓
    ├─→ 모든 작업 성공 → COMMIT (영구 저장)
    └─→ 하나라도 실패 → ROLLBACK (모두 취소)
```

---

## 🎯 ACID 속성

트랜잭션이 보장해야 하는 4가지 핵심 속성입니다.

### 1. Atomicity (원자성)

**"All or Nothing"** - 트랜잭션의 모든 작업이 성공하거나 모두 실패해야 합니다.

```sql
BEGIN TRANSACTION;

-- A 계좌에서 10만원 출금
UPDATE accounts SET balance = balance - 100000 WHERE id = 'A';

-- B 계좌로 10만원 입금
UPDATE accounts SET balance = balance + 100000 WHERE id = 'B';

-- 둘 다 성공 시 커밋
COMMIT;

-- 하나라도 실패 시 모두 롤백
-- ROLLBACK;
```

**실패 예시:**
```sql
BEGIN TRANSACTION;

UPDATE accounts SET balance = balance - 100000 WHERE id = 'A';  -- 성공

UPDATE accounts SET balance = balance + 100000 WHERE id = 'Z';  -- 실패 (존재하지 않는 계좌)

ROLLBACK;  -- A 계좌도 원래대로 복구됨
```

### 2. Consistency (일관성)

트랜잭션 전후로 데이터베이스는 **일관된 상태**를 유지해야 합니다. 모든 제약조건(Constraints)을 만족해야 합니다.

**제약조건 예시:**
```sql
-- 잔액은 항상 0 이상이어야 함
ALTER TABLE accounts
ADD CONSTRAINT chk_balance_positive CHECK (balance >= 0);

-- 이체 시도
BEGIN TRANSACTION;

UPDATE accounts SET balance = balance - 200000 WHERE id = 'A';
-- A의 잔액이 150000이면 제약조건 위반 → ROLLBACK

ROLLBACK;  -- 일관성 유지를 위해 자동 롤백
```

**일관성이 깨지는 예시 (트랜잭션 없이):**
```sql
-- 트랜잭션 없이 개별 쿼리 실행
UPDATE accounts SET balance = balance - 100000 WHERE id = 'A';  -- 성공

-- 애플리케이션 오류 발생 또는 네트워크 끊김

UPDATE accounts SET balance = balance + 100000 WHERE id = 'B';  -- 실행 안 됨

-- 결과: A 계좌에서 10만원 증발 (데이터 불일치)
```

### 3. Isolation (격리성)

동시에 실행되는 트랜잭션들이 서로 **간섭하지 않도록 격리**해야 합니다.

**격리 수준에 따라 발생하는 문제:**

```sql
-- Transaction A
BEGIN;
UPDATE products SET stock = 5 WHERE id = 1;
-- 아직 COMMIT 안 함

-- Transaction B (동시 실행)
BEGIN;
SELECT stock FROM products WHERE id = 1;
-- 어떤 값을 읽어야 할까?
-- - READ UNCOMMITTED: 5 (커밋 안 된 값)
-- - READ COMMITTED: 10 (커밋된 값)
```

### 4. Durability (지속성)

트랜잭션이 성공적으로 커밋되면, 그 결과는 **영구적으로 저장**되어야 합니다. 시스템 장애가 발생해도 데이터는 보존됩니다.

**보장 메커니즘:**
- Write-Ahead Logging (WAL)
- Redo Log
- Checkpoint

```sql
BEGIN TRANSACTION;

INSERT INTO orders (user_id, total_amount) VALUES (1, 50000);

COMMIT;  -- 디스크에 영구 저장됨

-- 이후 서버가 재시작되어도 주문 데이터는 남아있음
```

---

## 🔄 트랜잭션 상태 전이

```
        BEGIN
          ↓
      [Active]  ← 트랜잭션 실행 중
          ↓
    마지막 문장 실행
          ↓
  [Partially Committed]  ← 메모리상 완료, 디스크 저장 전
          ↓
     COMMIT 성공
          ↓
      [Committed]  ← 영구 저장 완료


오류 발생 시:
[Active] → [Failed] → [Aborted] → 종료
                ↓
             ROLLBACK
```

**상태별 설명:**
- **Active**: 트랜잭션 실행 중
- **Partially Committed**: 마지막 작업 완료, 디스크 기록 대기
- **Committed**: 성공적으로 완료됨
- **Failed**: 오류 발생
- **Aborted**: ROLLBACK 완료

---

## 📊 격리 수준 (Isolation Levels)

### 격리 수준이란?

동시에 실행되는 트랜잭션들이 서로에게 영향을 미치는 정도를 제어하는 설정입니다.

**트레이드오프:**
- 격리 수준 ↑ → 데이터 일관성 ↑, 동시성 ↓ (성능 저하)
- 격리 수준 ↓ → 데이터 일관성 ↓, 동시성 ↑ (성능 향상)

### 동시성 문제 현상

#### 1. Dirty Read (더티 리드)

**커밋되지 않은 데이터를 읽는 현상**

```sql
-- Time    Transaction A              Transaction B
-- T1      BEGIN;
-- T2      UPDATE products
--         SET price = 1000
--         WHERE id = 1;
--         (아직 COMMIT 안 함)
-- T3                                 BEGIN;
--                                    SELECT price FROM products
--                                    WHERE id = 1;
--                                    → 1000 읽음 (Dirty Read!)
-- T4      ROLLBACK;
--         (가격 변경 취소됨)
-- T5                                 -- B는 잘못된 값(1000)을 읽었음
```

**문제점**: Transaction B가 읽은 1000원은 실제로 반영되지 않은 값입니다.

#### 2. Non-Repeatable Read (반복 읽기 불가)

**같은 데이터를 두 번 읽었는데 값이 다른 현상**

```sql
-- Time    Transaction A              Transaction B
-- T1      BEGIN;
-- T2      SELECT stock FROM products
--         WHERE id = 1;
--         → 10
-- T3                                 BEGIN;
--                                    UPDATE products
--                                    SET stock = 5
--                                    WHERE id = 1;
--                                    COMMIT;
-- T4      SELECT stock FROM products
--         WHERE id = 1;
--         → 5 (다른 값!)
```

**문제점**: Transaction A 내에서 같은 쿼리를 두 번 실행했는데 결과가 다릅니다.

#### 3. Phantom Read (팬텀 리드)

**같은 조건으로 조회했는데 행 개수가 다른 현상**

```sql
-- Time    Transaction A              Transaction B
-- T1      BEGIN;
-- T2      SELECT COUNT(*) FROM orders
--         WHERE user_id = 1;
--         → 5개
-- T3                                 BEGIN;
--                                    INSERT INTO orders
--                                    (user_id, total_amount)
--                                    VALUES (1, 10000);
--                                    COMMIT;
-- T4      SELECT COUNT(*) FROM orders
--         WHERE user_id = 1;
--         → 6개 (Phantom Read!)
```

**문제점**: Transaction A가 같은 범위를 조회했는데 행이 추가되었습니다.

### 격리 수준 비교표

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read | 동시성 | 성능 |
|---------|-----------|---------------------|--------------|-------|------|
| **READ UNCOMMITTED** | ❌ 발생 | ❌ 발생 | ❌ 발생 | ⭐⭐⭐⭐⭐ | 최고 |
| **READ COMMITTED** | ✅ 방지 | ❌ 발생 | ❌ 발생 | ⭐⭐⭐⭐ | 높음 |
| **REPEATABLE READ** | ✅ 방지 | ✅ 방지 | ❌ 발생* | ⭐⭐⭐ | 중간 |
| **SERIALIZABLE** | ✅ 방지 | ✅ 방지 | ✅ 방지 | ⭐⭐ | 낮음 |

**\* MySQL InnoDB는 REPEATABLE READ에서도 Phantom Read를 방지합니다 (MVCC 덕분)**

### 각 격리 수준 상세 설명

#### READ UNCOMMITTED (가장 낮은 격리)

```sql
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
```

**특징:**
- 커밋되지 않은 데이터도 읽을 수 있음
- 거의 사용하지 않음 (데이터 정합성 보장 안 됨)

**적합한 케이스:**
- 대략적인 통계 (정확도가 중요하지 않음)
- 실시간 모니터링 (빠른 응답 필요)

**예시:**
```sql
-- Transaction A
BEGIN;
UPDATE users SET login_count = login_count + 1;
-- COMMIT 전

-- Transaction B (READ UNCOMMITTED)
SELECT SUM(login_count) FROM users;  -- 커밋 안 된 값 포함됨
```

---

#### READ COMMITTED (기본값 - PostgreSQL, Oracle)

```sql
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

**특징:**
- 커밋된 데이터만 읽기 가능
- 대부분의 웹 애플리케이션에 적합

**동작 방식:**
```sql
-- Transaction A
BEGIN;
UPDATE products SET price = 2000 WHERE id = 1;
-- COMMIT 전

-- Transaction B (READ COMMITTED)
SELECT price FROM products WHERE id = 1;
→ 1000 (커밋된 값만 읽음)

-- Transaction A
COMMIT;

-- Transaction B
SELECT price FROM products WHERE id = 1;
→ 2000 (커밋 후 새로운 값 읽음)
```

**장점:**
- Dirty Read 방지
- 높은 동시성 유지

**단점:**
- Non-Repeatable Read 발생 가능

### 💡 전문가 의견: 언제 READ COMMITTED로 격리 수준을 낮출까?

#### 김데이터 (DBA, 20년차)
> "REPEATABLE READ는 Undo Log를 오래 유지해야 하기 때문에 디스크 공간을 많이 차지합니다. READ COMMITTED로 내렸을 때 영향이 없는 트랜잭션이라면 내리는 게 좋습니다."

**Undo Log 문제:**
```
REPEATABLE READ (오래 실행되는 트랜잭션)
↓
Undo Log 계속 쌓임 (스냅샷 유지)
↓
디스크 공간 부족
↓
성능 저하
```

#### 박트래픽 (성능 전문가, 15년차)
> "트래픽이 많은 서비스에서는 격리 수준을 한 단계 낮추는 것만으로도 TPS를 30% 향상시킬 수 있습니다. 단, 비즈니스 로직에 영향이 없는지 반드시 검증해야 합니다."

**READ COMMITTED로 충분한 케이스:**

```java
// ✅ 단순 조회 - READ COMMITTED로 충분
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public List<Product> getProducts() {
    // 상품 목록 조회
    // 조회 중에 다른 사람이 상품 가격을 바꿔도 괜찮음
    return productRepository.findAll();
}

// ✅ 단일 작업 - READ COMMITTED로 충분
@Transactional(isolation = Isolation.READ_COMMITTED)
public void addReview(Long productId, String content) {
    // 리뷰 추가
    // 같은 리뷰를 두 번 읽을 일이 없음
    Review review = new Review(productId, content);
    reviewRepository.save(review);
}

// ❌ REPEATABLE READ가 필요한 경우: 통계 계산
@Transactional(isolation = Isolation.REPEATABLE_READ)
public OrderStatistics calculateDailyStatistics() {
    // 주문 통계 계산
    // 계산 중에 데이터가 바뀌면 안 됨!
    int totalOrders = orderRepository.countToday();
    int totalAmount = orderRepository.sumTodayAmount();
    return new OrderStatistics(totalOrders, totalAmount);
}
```

#### 이금융 (금융권, 12년차)
> "금융권에서는 격리 수준을 낮추는 것을 권장하지 않습니다. 성능보다 정확성이 우선이기 때문입니다. 다만 로그 조회, 통계 조회 같은 읽기 전용 작업은 READ COMMITTED를 사용합니다."

**격리 수준 선택 기준:**

| 상황 | 추천 격리 수준 | 이유 |
|------|--------------|------|
| **단순 목록 조회** | READ COMMITTED | 조회 중 데이터 변경 허용 |
| **통계 계산** | REPEATABLE READ | 계산 중 데이터 일관성 필요 |
| **금융 거래** | SERIALIZABLE | 완벽한 정합성 필요 |
| **단일 INSERT** | READ COMMITTED | 한 번만 실행, 재조회 없음 |
| **복잡한 계산 후 UPDATE** | REPEATABLE READ | 계산 기반 데이터 일관성 필요 |

**MySQL 설정 (전역 변경):**
```yaml
# my.cnf
[mysqld]
transaction-isolation = READ-COMMITTED

# 또는
innodb_undo_log_truncate = ON
innodb_max_undo_log_size = 1G  # Undo Log 최대 크기 제한
```

**Spring Boot 설정 (케이스별 적용):**
```java
@Service
public class OrderService {

    // 기본값: REPEATABLE READ (application.yml에 설정)
    @Transactional
    public void createOrder(OrderRequest request) {
        // 중요한 작업은 높은 격리 수준
    }

    // 명시적으로 READ COMMITTED 사용
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Order> getRecentOrders(Long userId) {
        // 단순 조회는 낮은 격리 수준
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
```

---

#### REPEATABLE READ (기본값 - MySQL InnoDB)

```sql
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
```

**특징:**
- 트랜잭션 내에서 같은 데이터를 여러 번 읽어도 같은 값
- **MVCC(Multi-Version Concurrency Control)** 사용

**동작 방식:**
```sql
-- Transaction A (REPEATABLE READ)
BEGIN;
SELECT price FROM products WHERE id = 1;
→ 1000

-- Transaction B
BEGIN;
UPDATE products SET price = 2000 WHERE id = 1;
COMMIT;

-- Transaction A (같은 트랜잭션 내)
SELECT price FROM products WHERE id = 1;
→ 여전히 1000! (스냅샷 읽기)

COMMIT;

-- Transaction A 종료 후 새 트랜잭션
SELECT price FROM products WHERE id = 1;
→ 2000
```

**MVCC 개념:**
- 각 트랜잭션은 시작 시점의 **스냅샷**을 읽음
- 다른 트랜잭션의 변경 사항이 보이지 않음
- Undo Log를 활용하여 이전 버전 유지

**MySQL InnoDB의 Phantom Read 방지:**
```sql
-- Transaction A (REPEATABLE READ)
BEGIN;
SELECT * FROM orders WHERE user_id = 1;
→ 5개

-- Transaction B
INSERT INTO orders (user_id, total_amount) VALUES (1, 10000);
COMMIT;

-- Transaction A
SELECT * FROM orders WHERE user_id = 1;
→ 여전히 5개! (Phantom Read 방지됨)
```

### 💡 전문가 의견: MySQL vs PostgreSQL REPEATABLE READ 차이

같은 REPEATABLE READ 격리 수준이라도 DBMS마다 내부 구현 방식이 다르기 때문에 동작이 다릅니다.

#### 김데이터 (DBA, 20년차)
> "MySQL과 PostgreSQL은 MVCC 구현 방식이 다릅니다. MySQL은 Undo Log 기반이고, PostgreSQL은 Tuple Versioning 방식입니다. 이 차이로 인해 동시 업데이트 시 PostgreSQL은 에러를 발생시키지만 MySQL은 대기 후 실행됩니다."

#### 실무 시나리오 비교

```sql
-- 초기 상태: products 테이블에 id=1, stock=10인 상품 존재

-- MySQL (REPEATABLE READ)
-- Transaction A
BEGIN;
SELECT stock FROM products WHERE id = 1;  -- 10
UPDATE products SET stock = 5 WHERE id = 1;

-- Transaction B (동시 실행)
BEGIN;
UPDATE products SET stock = 8 WHERE id = 1;  -- ⏰ A가 끝날 때까지 대기

-- Transaction A
COMMIT;  -- 이제 B가 실행됨

-- ✅ MySQL: 정상 동작 (에러 없음, B의 UPDATE가 실행됨)
```

```sql
-- PostgreSQL (REPEATABLE READ)
-- Transaction A
BEGIN;
SELECT stock FROM products WHERE id = 1;  -- 10
UPDATE products SET stock = 5 WHERE id = 1;

-- Transaction B (동시 실행)
BEGIN;
UPDATE products SET stock = 8 WHERE id = 1;  -- ⏰ 대기

-- Transaction A
COMMIT;

-- Transaction B
-- ❌ PostgreSQL: 에러 발생!
-- ERROR: could not serialize access due to concurrent update
```

#### 박트래픽 (성능 전문가, 15년차)
> "PostgreSQL에서는 재시도 로직이 필수입니다. MySQL보다 엄격한 정합성을 보장하지만, 애플리케이션 레벨에서 예외 처리를 해야 합니다."

**PostgreSQL 재시도 패턴:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void updateStockWithRetry(Long productId, int newStock) {
    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            Product product = productRepository.findById(productId).orElseThrow();
            product.setStock(newStock);
            return;  // 성공
        } catch (OptimisticLockException | CannotAcquireLockException e) {
            if (attempt == maxRetries - 1) throw e;
            try {
                Thread.sleep(100 * (attempt + 1));  // Exponential Backoff
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
    }
}
```

#### 정스타트업 (CTO, 7년차)
> "처음 프로젝트를 시작할 때는 DBMS별 차이를 모르고 MySQL 코드를 PostgreSQL에 그대로 이식했다가 프로덕션에서 에러가 속출했던 경험이 있습니다. 반드시 테스트 환경도 동일한 DBMS를 사용해야 합니다."

**DBMS별 차이점 요약:**

| DBMS | REPEATABLE READ 구현 방식 | 동시 업데이트 동작 | 재시도 필요 |
|------|--------------------------|----------------|-----------|
| **MySQL** | MVCC (Undo Log) | 대기 후 실행 가능 | ❌ 불필요 |
| **PostgreSQL** | MVCC (Tuple Versioning) | 에러 발생 (Serialization Failure) | ✅ 필수 |

---

#### SERIALIZABLE (가장 높은 격리)

```sql
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
```

**특징:**
- 트랜잭션을 순차적으로 실행한 것처럼 보장
- 모든 SELECT에 자동으로 LOCK
- 성능 문제로 특수한 경우만 사용

**동작 방식:**
```sql
-- Transaction A (SERIALIZABLE)
BEGIN;
SELECT * FROM products WHERE category = 'laptop';
-- 모든 laptop 행에 Shared Lock 획득

-- Transaction B
INSERT INTO products (name, category, price)
VALUES ('New Laptop', 'laptop', 1500000);
-- Transaction A가 COMMIT할 때까지 대기!

-- Transaction A
COMMIT;  -- Lock 해제

-- Transaction B
-- 이제 INSERT 실행됨
```

**적합한 케이스:**
- 금융 거래 (정확성이 최우선)
- 회계 시스템
- 감사 추적이 필요한 경우

**주의사항:**
- 처리량(TPS)이 급격히 감소 (10~50%)
- Deadlock 발생 확률 증가

---

## 🔧 격리 수준 설정 및 확인

### MySQL

```sql
-- 현재 격리 수준 확인
SELECT @@GLOBAL.transaction_isolation, @@SESSION.transaction_isolation;

-- 세션 레벨 변경
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 다음 트랜잭션만 변경
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- 전역 설정 (서버 재시작 시 유지됨)
SET GLOBAL TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- my.cnf 파일 설정
[mysqld]
transaction-isolation = READ-COMMITTED
```

### PostgreSQL

```sql
-- 현재 격리 수준 확인
SHOW transaction_isolation;

-- 특정 트랜잭션에만 적용
BEGIN ISOLATION LEVEL REPEATABLE READ;
-- 쿼리 실행
COMMIT;

-- 세션 레벨 변경
SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- postgresql.conf 파일 설정
default_transaction_isolation = 'repeatable read'
```

---

## 🎯 실무 권장 사항

### 격리 수준 선택 가이드

```
START
  ↓
데이터 정확성이 매우 중요한가? (금융, 결제)
  ├─ YES → SERIALIZABLE 또는 REPEATABLE READ
  └─ NO → 계속
         ↓
동시 접속이 많은가? (높은 TPS 필요)
  ├─ YES → READ COMMITTED (PostgreSQL 기본값)
  └─ NO → REPEATABLE READ (MySQL 기본값)
         ↓
대략적인 통계만 필요한가?
  ├─ YES → READ UNCOMMITTED
  └─ NO → READ COMMITTED
```

### 시나리오별 추천

| 시나리오 | 추천 격리 수준 | 이유 |
|---------|--------------|------|
| **일반 웹 애플리케이션** | READ COMMITTED | 성능과 정합성 균형 |
| **금융 거래** | SERIALIZABLE | 완벽한 정합성 필요 |
| **재고 관리** | REPEATABLE READ | 트랜잭션 내 일관성 보장 |
| **조회수 집계** | READ UNCOMMITTED | 빠른 응답, 정확도 덜 중요 |
| **게시판 댓글** | READ COMMITTED | 동시 작성 빈번 |

### 성능 vs 정합성 트레이드오프

```
SERIALIZABLE      성능 ↓ / 정합성 ↑
    ↑
REPEATABLE READ   균형점 (MySQL 기본)
    ↑
READ COMMITTED    균형점 (PostgreSQL 기본)
    ↑
READ UNCOMMITTED  성능 ↑ / 정합성 ↓
```

---

## 🔑 Primary Key 설계 가이드

### 왜 Primary Key가 중요한가?

Primary Key는 단순히 데이터를 식별하는 것 이상의 역할을 합니다. DBMS 내부에서 모든 Secondary Index는 Primary Key를 참조하기 때문에, **PK가 변경되면 모든 인덱스가 업데이트**되어야 합니다.

### 💡 전문가 의견: Primary Key 선택 전략

#### 김데이터 (DBA, 20년차)
> "PK가 변경되면 PK를 바라보고 있는 모든 인덱스들이 전체적으로 업데이트가 일어나야 합니다. PK는 변경이 일어나면 안 될 것들 위주로 구성해야 합니다."

**Secondary Index가 PK를 참조하는 구조 (InnoDB):**

```
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(100),
    name VARCHAR(50)
);

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_name ON users(name);

인덱스 내부 구조:
idx_email 인덱스 트리:
    [alice@example.com, PK=1]
    [bob@example.com, PK=2]
    [charlie@example.com, PK=3]

idx_name 인덱스 트리:
    [Alice, PK=1]
    [Bob, PK=2]
    [Charlie, PK=3]

만약 PK=1이 PK=999로 변경되면?
→ idx_email과 idx_name 모두 업데이트 필요! (매우 느림)
```

#### 박트래픽 (성능 전문가, 15년차)
> "이메일이나 사용자명처럼 변경 가능한 필드를 PK로 사용하면 안 됩니다. Auto-increment ID나 UUID를 사용하세요."

**❌ 나쁜 PK 선택: 이메일 (변경 가능)**

```sql
CREATE TABLE users (
    email VARCHAR(100) PRIMARY KEY,  -- 이메일은 바뀔 수 있음!
    name VARCHAR(50),
    created_at TIMESTAMP
);

-- 인덱스들 (자동으로 email을 참조함)
CREATE INDEX idx_name ON users(name);  -- (name, email)
CREATE INDEX idx_created ON users(created_at);  -- (created_at, email)

-- 이메일 변경 시
UPDATE users SET email = 'new@example.com'
WHERE email = 'old@example.com';
-- → 모든 인덱스 업데이트 필요! (매우 느림)
-- → users 테이블을 참조하는 모든 Foreign Key도 업데이트!

-- 성능:
-- - 단순 컬럼 변경: 10ms
-- - PK 변경 (인덱스 3개): 500ms+
```

**✅ 좋은 PK 선택: Auto-increment ID (절대 안 바뀜)**

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,  -- 절대 안 바뀜!
    email VARCHAR(100) UNIQUE,  -- 이메일은 유니크 제약만
    name VARCHAR(50),
    created_at TIMESTAMP
);

-- 이메일 변경 시
UPDATE users SET email = 'new@example.com'
WHERE id = 123;
-- → 하나의 컬럼만 변경! (빠름)
-- → 인덱스는 자동 업데이트 (PK는 안 바뀜)

-- 성능:
-- - 컬럼 변경: 10ms
```

#### 최아키텍트 (MSA, 10년차)
> "MSA 환경에서는 UUID를 PK로 사용하는 경우가 많습니다. 각 서비스가 독립적으로 ID를 생성할 수 있어 분산 환경에 적합합니다."

**UUID vs Auto-increment 비교:**

| 특징 | Auto-increment | UUID |
|------|---------------|------|
| **크기** | 8 bytes (BIGINT) | 16 bytes (BINARY(16)) |
| **순차성** | ✅ 순차적 | ❌ 랜덤 |
| **인덱스 성능** | ✅ 좋음 (B+Tree 효율적) | ⚠️ 나쁨 (페이지 분할 빈번) |
| **분산 생성** | ❌ 불가능 (DB 의존) | ✅ 가능 (앱에서 생성) |
| **예측 가능성** | ❌ 예측 가능 (보안 취약) | ✅ 예측 불가능 |
| **적합한 환경** | 단일 DB, 높은 성능 요구 | MSA, 분산 시스템 |

**JPA에서 UUID 사용:**
```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    private String name;
    private Integer stock;
}

// MySQL 스키마
CREATE TABLE products (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(100),
    stock INT
);
```

#### 정스타트업 (CTO, 7년차)
> "초기에는 Auto-increment로 시작하고, 서비스가 커지면서 분산 환경으로 전환할 때 UUID로 마이그레이션했습니다. 처음부터 UUID를 쓰면 초기 성능이 떨어질 수 있으니 신중하게 선택하세요."

### 복합 PK는 언제 사용할까?

#### 중간 테이블 (Many-to-Many 관계)

```java
// ✅ 복합 PK 적합: UserCoupon (사용자-쿠폰 매핑)
@Entity
@IdClass(UserCouponId.class)
public class UserCoupon {
    @Id
    private Long userId;  // 복합 PK 1

    @Id
    private Long couponId;  // 복합 PK 2

    private Instant issuedAt;

    // userId, couponId 둘 다 절대 안 바뀜!
}

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserCouponId implements Serializable {
    private Long userId;
    private Long couponId;

    @Override
    public boolean equals(Object o) { /* ... */ }

    @Override
    public int hashCode() { /* ... */ }
}

// MySQL 스키마
CREATE TABLE user_coupons (
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, coupon_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);
```

**복합 PK 주의사항:**
- 모든 컬럼이 절대 변경되지 않는 경우에만 사용
- 복합 PK를 Foreign Key로 참조하는 경우 조인 성능 저하
- 가능하면 대리 키(Surrogate Key) 사용 권장

### PK 선택 체크리스트

✅ **Good PK:**
- 절대 변경되지 않음
- 짧은 크기 (BIGINT, UUID)
- 순차성 (Auto-increment)
- NOT NULL 보장

❌ **Bad PK:**
- 변경 가능 (이메일, 전화번호)
- 너무 긴 크기 (VARCHAR(255))
- 비즈니스 의미 포함 (주문번호, 상품코드)
- 복잡한 복합 키

---

## 💡 Best Practices

### 1. 대부분의 경우 DBMS 기본값을 사용하라

```java
// ❌ 나쁜 예: 모든 트랜잭션에 SERIALIZABLE 적용
@Transactional(isolation = Isolation.SERIALIZABLE)
public void updateProduct(Product product) {
    // 불필요하게 높은 격리 수준
}

// ✅ 좋은 예: 필요한 곳에만 높은 격리 수준 적용
@Transactional  // 기본값 사용
public void updateProduct(Product product) {
    productRepository.save(product);
}

@Transactional(isolation = Isolation.SERIALIZABLE)  // 특별히 필요한 경우만
public void processPayment(Payment payment) {
    // 정확성이 매우 중요한 결제 처리
}
```

### 2. 트랜잭션 크기를 최소화하라

```java
// ❌ 나쁜 예: 불필요한 작업을 트랜잭션 내에서
@Transactional
public void createOrder(OrderRequest request) {
    Order order = orderRepository.save(new Order(request));

    // 외부 API 호출 (5초 소요) - 트랜잭션 길어짐!
    externalService.notifyPartner(order);

    // 이메일 발송 (3초 소요) - 트랜잭션 길어짐!
    emailService.sendOrderConfirmation(order);
}

// ✅ 좋은 예: 트랜잭션 외부로 분리
@Transactional
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}

public void processOrderCreation(OrderRequest request) {
    // 트랜잭션: DB 작업만
    Order order = createOrder(request);

    // 트랜잭션 외부: 외부 API 호출
    externalService.notifyPartner(order);
    emailService.sendOrderConfirmation(order);
}
```

### 3. READ ONLY 트랜잭션 활용

```java
// 읽기 전용 트랜잭션: 성능 최적화
@Transactional(readOnly = true)
public List<Product> getProducts() {
    return productRepository.findAll();
}

// MySQL: SELECT 쿼리 최적화
// PostgreSQL: MVCC 스냅샷 생성 생략
```

---

## 📚 참고 자료

### 공식 문서
- [MySQL - InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [PostgreSQL - Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html)

### 도서
- Martin Kleppmann - **Designing Data-Intensive Applications** (Chapter 7: Transactions)
- Alex Petrov - **Database Internals** (Chapter 5: Transaction Processing)

### 아티클
- [Wikipedia - ACID](https://en.wikipedia.org/wiki/ACID)
- [Wikipedia - Isolation (database systems)](https://en.wikipedia.org/wiki/Isolation_(database_systems))

---

**작성일**: 2025-11-18
**버전**: 1.0
