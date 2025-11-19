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
