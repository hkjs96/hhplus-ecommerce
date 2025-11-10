# ERD 변경 이력 (CHANGELOG)

## v2.0 (2025-01-10) - 5가지 개선사항 반영

### 📊 요약

**테이블 개수:** 10개 → **12개** (+2)
**변경 유형:** 테이블 추가 2개, 구조 변경 2개, 문서화 개선 1개

---

## ✅ 개선 사항 상세

### 1. **balance_history 테이블 추가** ⭐⭐⭐ (Critical)

**우선순위:** P0 (필수)

**문제점:**
- 현재 ERD에는 `users.balance` 필드만 존재
- 포인트 충전/사용 이력을 추적할 수 없음
- 감사(Audit) 불가능
- 잔액 불일치 발생 시 디버깅 어려움
- User Story US-007, US-008과 관련

**해결 방안:**
```sql
Table balance_history {
  id varchar [primary key, note: 'BH-YYYYMMDD-{시퀀스}']
  user_id varchar [not null]
  type varchar [not null, note: 'CHARGE, USE, REFUND']
  amount decimal(10,2) [not null, note: '양수/음수']
  balance_before decimal(10,2) [not null]
  balance_after decimal(10,2) [not null]
  reference_type varchar [note: 'ORDER, ADMIN_ADJUSTMENT']
  reference_id varchar
  reason text
  created_at datetime [not null]
}
```

**영향:**
- ✅ 포인트 변동 완전 추적 (감사 필수)
- ✅ 데이터 무결성 검증 가능
- ✅ 잔액 불일치 디버깅 용이

**관련 쿼리:**
```sql
-- 포인트 차감 + 이력 기록 (트랜잭션)
INSERT INTO balance_history (
  id, user_id, type, amount,
  balance_before, balance_after,
  reference_type, reference_id, reason, created_at
) VALUES (
  :id, :userId, 'USE', -:amount,
  :balanceBefore, :balanceAfter,
  'ORDER', :orderId, '주문 결제', NOW()
);
```

---

### 2. **outbox_messages 테이블 추가** ⭐⭐⭐ (Critical)

**우선순위:** P0 (필수)

**문제점:**
- README.md와 요구사항에는 Outbox 패턴 명시됨
- 외부 API 전송 실패 시 재시도 메커니즘이 ERD에 없음
- Retry 전략(1분→5분→30분)을 구현할 테이블 부재
- 가용성 패턴(Retry, Fallback)이 불완전

**해결 방안:**
```sql
Table outbox_messages {
  id varchar [primary key, note: 'OUTBOX-YYYYMMDD-{시퀀스}']
  message_type varchar [not null, note: 'ORDER_COMPLETED, PAYMENT_SUCCESS']
  payload text [not null, note: 'JSON 형식']
  reference_type varchar [not null, note: 'ORDER, PAYMENT']
  reference_id varchar [not null]
  status varchar [not null, note: 'PENDING, SENT, FAILED, PERMANENT_FAILURE']
  retry_count integer [not null, default: 0]
  max_retry integer [not null, default: 3]
  next_retry_at datetime [note: '다음 재시도 시각']
  error_message text
  sent_at datetime
  created_at datetime [not null]
  updated_at datetime [not null]
}
```

**영향:**
- ✅ 외부 API 장애 시에도 데이터 유실 방지
- ✅ Exponential Backoff 재시도 메커니즘 구현
- ✅ 가용성 패턴 (Retry, Fallback) 완전 구현

**재시도 로직:**
```sql
-- Exponential Backoff
UPDATE outbox_messages
SET retry_count = retry_count + 1,
    next_retry_at = CASE
      WHEN retry_count = 0 THEN NOW() + INTERVAL 1 MINUTE
      WHEN retry_count = 1 THEN NOW() + INTERVAL 5 MINUTE
      WHEN retry_count = 2 THEN NOW() + INTERVAL 30 MINUTE
      ELSE NULL
    END,
    status = CASE
      WHEN retry_count >= max_retry - 1 THEN 'PERMANENT_FAILURE'
      ELSE 'PENDING'
    END
WHERE id = :id;
```

---

### 3. **stock 테이블 PK 구조 개선** ⭐⭐ (Important)

**우선순위:** P1 (권장)

**문제점:**
- 현재: `id` (PK) + `(product_id, warehouse_id)` Unique Index
- `id` 필드가 불필요한 surrogate key
- 인덱스 중복 (PK 인덱스 + Unique 인덱스)
- 복합 키로 충분히 유일성 보장 가능

**변경 내용:**
```sql
-- Before
Table stock {
  id varchar [primary key]
  product_id varchar [not null]
  warehouse_id varchar [not null]
  ...
  indexes {
    (product_id, warehouse_id) [unique]
  }
}

-- After
Table stock {
  product_id varchar [primary key]
  warehouse_id varchar [primary key, default: 'DEFAULT']
  quantity integer [not null, default: 0]
  version bigint [not null, default: 0]
  updated_at datetime [not null]
}
```

**장점:**
- ✅ 인덱스 중복 제거 (PK 인덱스 = Unique 인덱스)
- ✅ 저장 공간 절약
- ✅ 쿼리 성능 향상

**단점:**
- ⚠️ JPA에서 복합 PK는 `@EmbeddedId` 필요 (구현 복잡도 증가)

**트레이드오프:**
- 순수 DB 관점: 복합 PK가 우수 (정규화, 성능)
- JPA 편의성: surrogate key가 편리 (단순)

**결론:** 복합 PK 채택 (설계 일관성 우선)

**JPA 구현 예시:**
```java
@Embeddable
@EqualsAndHashCode
public class StockId implements Serializable {
    private String productId;
    private String warehouseId = "DEFAULT";
}

@Entity
public class Stock {
    @EmbeddedId
    private StockId id;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Version
    private Long version = 0L;
}
```

---

### 4. **orders 테이블 필드 추가** ⭐ (Nice to have)

**우선순위:** P2 (선택)

**문제점:**
- `status = 'CANCELLED'`인데 취소 시각이 없음
- 취소 사유를 기록할 수 없음
- 감사(Audit) 목적으로 필요
- CS 대응 시 불편함

**추가 필드:**
```sql
Table orders {
  ...
  cancelled_at datetime [note: '주문 취소 시각 (nullable) - 감사 목적']
  cancellation_reason text [note: '취소 사유 (nullable) - CS 대응용']

  indexes {
    cancelled_at  -- 추가
  }
}
```

**영향:**
- ✅ 주문 취소 감사 추적 강화
- ✅ CS 대응 용이 (취소 사유 확인)
- ✅ 주문 통계 분석 시 유용

**사용 예시:**
```java
public void cancelOrder(String orderId, String reason) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    order.cancel(reason);
    order.setCancelledAt(LocalDateTime.now());
    order.setCancellationReason(reason);
}
```

---

### 5. **ID 생성 전략 문서화** ⭐ (Documentation)

**우선순위:** P2 (선택)

**문제점:**
- varchar 타입의 ID (P001, ORDER-YYYYMMDD-XXX) 사용
- 생성 규칙이 ERD에 명시되지 않음
- 개발자마다 다르게 해석 가능

**개선 내용:**
ERD의 모든 테이블 note에 ID 생성 전략 명시

| 테이블 | ID 생성 규칙 | 예시 |
|--------|-------------|------|
| products | `P{3자리 숫자}` | P001, P002, P999 |
| users | `U{3자리 숫자}` | U001, U010, U999 |
| coupons | `C{3자리 숫자}` | C001, C010, C999 |
| orders | `ORDER-YYYYMMDD-{3자리 시퀀스}` | ORDER-20250110-001, ORDER-20250110-999 |
| order_items | `OI-YYYYMMDD-{시퀀스}` | OI-20250110-001 |
| carts | `CART-{userId}` | CART-U001 |
| cart_items | `CI-YYYYMMDD-{시퀀스}` | CI-20250110-001 |
| user_coupons | `UC-YYYYMMDD-{3자리 시퀀스}` | UC-20250110-001, UC-20250110-999 |
| stock_history | `SH-YYYYMMDD-{시퀀스}` | SH-20250110-001 |
| balance_history | `BH-YYYYMMDD-{시퀀스}` | BH-20250110-001 |
| outbox_messages | `OUTBOX-YYYYMMDD-{시퀀스}` | OUTBOX-20250110-001 |

**영향:**
- ✅ ID 생성 규칙 명확화
- ✅ 구현 일관성 확보
- ✅ 코드 리뷰 용이

---

## 📋 변경 전후 비교

### 테이블 개수

| 항목 | v1.0 (Before) | v2.0 (After) | 변경 |
|------|--------------|--------------|------|
| **상품 관리** | 3개 (products, stock, stock_history) | 3개 | - |
| **장바구니** | 2개 (carts, cart_items) | 2개 | - |
| **주문/결제** | 2개 (orders, order_items) | 2개 | - |
| **쿠폰 시스템** | 2개 (coupons, user_coupons) | 2개 | - |
| **사용자** | 1개 (users) | **2개** (users, balance_history) | **+1** ⭐ |
| **외부 연동** | **0개** | **1개** (outbox_messages) | **+1** ⭐ |
| **합계** | **10개** | **12개** | **+2** |

### 필드 변경

| 테이블 | 변경 항목 | Before | After |
|--------|----------|--------|-------|
| **stock** | PK 구조 | `id` (PK) + Unique Index | 복합 PK (product_id, warehouse_id) |
| **orders** | 필드 추가 | - | `cancelled_at`, `cancellation_reason` |
| **모든 테이블** | 문서화 | note 누락 | ID 생성 규칙 명시 |

---

## 🔍 마이그레이션 가이드

### Week 4 구현 시 참고사항

#### 1. balance_history 테이블 생성
```sql
CREATE TABLE balance_history (
  id VARCHAR(50) PRIMARY KEY,
  user_id VARCHAR(50) NOT NULL,
  type VARCHAR(20) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  balance_before DECIMAL(10,2) NOT NULL,
  balance_after DECIMAL(10,2) NOT NULL,
  reference_type VARCHAR(50),
  reference_id VARCHAR(50),
  reason TEXT,
  created_at DATETIME NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
  INDEX idx_balance_history_user_id (user_id),
  INDEX idx_balance_history_reference (reference_type, reference_id),
  INDEX idx_balance_history_created_at (created_at DESC)
);
```

#### 2. outbox_messages 테이블 생성
```sql
CREATE TABLE outbox_messages (
  id VARCHAR(50) PRIMARY KEY,
  message_type VARCHAR(50) NOT NULL,
  payload TEXT NOT NULL,
  reference_type VARCHAR(50) NOT NULL,
  reference_id VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  max_retry INTEGER NOT NULL DEFAULT 3,
  next_retry_at DATETIME,
  error_message TEXT,
  sent_at DATETIME,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_outbox_status (status),
  INDEX idx_outbox_retry (status, next_retry_at),
  INDEX idx_outbox_reference (reference_type, reference_id),
  INDEX idx_outbox_created_at (created_at)
);
```

#### 3. stock 테이블 재생성 (복합 PK)
```sql
-- 기존 테이블 백업
CREATE TABLE stock_backup AS SELECT * FROM stock;

-- 기존 테이블 삭제
DROP TABLE stock;

-- 새 테이블 생성 (복합 PK)
CREATE TABLE stock (
  product_id VARCHAR(50) NOT NULL,
  warehouse_id VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
  quantity INTEGER NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (product_id, warehouse_id),
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

-- 데이터 복원
INSERT INTO stock (product_id, warehouse_id, quantity, version, updated_at)
SELECT product_id, warehouse_id, quantity, version, updated_at FROM stock_backup;
```

#### 4. orders 테이블 ALTER
```sql
ALTER TABLE orders
ADD COLUMN cancelled_at DATETIME NULL,
ADD COLUMN cancellation_reason TEXT NULL;

CREATE INDEX idx_orders_cancelled_at ON orders(cancelled_at);
```

---

## 📊 영향 분석

### 애플리케이션 레이어 영향

#### 1. balance_history 추가 영향
- **Domain Layer**: `BalanceHistory` Entity 추가
- **Infrastructure Layer**: `BalanceHistoryRepository` 추가
- **Application Layer**: 포인트 충전/사용 시 이력 기록 로직 추가

```java
@Transactional
public void chargeBalance(String userId, Long amount) {
    User user = userRepository.findByIdWithLock(userId).orElseThrow();

    Long balanceBefore = user.getBalance();
    user.chargeBalance(amount);
    Long balanceAfter = user.getBalance();

    // 이력 기록 추가
    BalanceHistory history = BalanceHistory.builder()
        .userId(userId)
        .type(BalanceType.CHARGE)
        .amount(amount)
        .balanceBefore(balanceBefore)
        .balanceAfter(balanceAfter)
        .build();

    balanceHistoryRepository.save(history);
}
```

#### 2. outbox_messages 추가 영향
- **Domain Layer**: `OutboxMessage` Entity 추가
- **Infrastructure Layer**: `OutboxMessageRepository` 추가, Retry 스케줄러 추가
- **Application Layer**: 외부 API 전송 실패 시 Outbox 저장 로직 추가

```java
@Async
public void sendOrderData(Order order) {
    try {
        externalApiClient.sendOrder(order);
    } catch (Exception e) {
        // Outbox에 저장
        OutboxMessage message = OutboxMessage.builder()
            .messageType("ORDER_COMPLETED")
            .payload(objectMapper.writeValueAsString(order))
            .referenceType("ORDER")
            .referenceId(order.getId())
            .status(OutboxStatus.PENDING)
            .build();

        outboxMessageRepository.save(message);
    }
}
```

#### 3. stock 복합 PK 영향
- **Domain Layer**: `StockId` (복합 PK 클래스) 추가
- **Infrastructure Layer**: Repository 조회 메서드 변경
- **Application Layer**: Stock 조회 시 복합 키 사용

```java
// Before
Stock stock = stockRepository.findById(stockId).orElseThrow();

// After
StockId stockId = new StockId(productId, warehouseId);
Stock stock = stockRepository.findById(stockId).orElseThrow();
```

#### 4. orders 필드 추가 영향
- **Domain Layer**: `Order.cancel()` 메서드 추가
- **Application Layer**: 주문 취소 로직 업데이트

```java
public void cancelOrder(String orderId, String reason) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    order.cancel(reason, LocalDateTime.now());
    orderRepository.save(order);
}
```

---

## ✅ 검증 체크리스트

### v2.0 ERD 적용 확인

- [ ] **balance_history 테이블 생성 완료**
  - [ ] 테이블 DDL 실행
  - [ ] 인덱스 생성 확인
  - [ ] FK 제약조건 확인

- [ ] **outbox_messages 테이블 생성 완료**
  - [ ] 테이블 DDL 실행
  - [ ] 인덱스 생성 확인
  - [ ] 재시도 스케줄러 구현

- [ ] **stock 테이블 복합 PK 변경 완료**
  - [ ] 기존 데이터 백업
  - [ ] 테이블 재생성
  - [ ] 데이터 복원 확인
  - [ ] JPA Entity 업데이트 (`@EmbeddedId`)

- [ ] **orders 테이블 필드 추가 완료**
  - [ ] `cancelled_at` 필드 추가
  - [ ] `cancellation_reason` 필드 추가
  - [ ] 인덱스 생성 확인

- [ ] **ID 생성 전략 문서화 완료**
  - [ ] 모든 테이블 note에 ID 규칙 명시
  - [ ] 개발팀 공유

---

## 🚀 다음 단계

### Week 4 구현 순서

1. **Step 7: Database Integration**
   - ERD v2.0 기반 DDL 생성
   - JPA Entity 작성 (복합 PK 포함)
   - Repository 구현
   - 기존 In-Memory → JPA 마이그레이션

2. **Step 8: 고급 동시성 제어**
   - Pessimistic Lock (포인트)
   - Optimistic Lock (재고, 쿠폰)
   - 동시성 테스트 작성

3. **추가 구현 (선택)**
   - BalanceHistory 이력 조회 API
   - Outbox 재시도 스케줄러
   - 주문 취소 API

---

## 📚 참고 문서

- [ERD v2.0 (erd.md)](./erd.md)
- [요구사항 명세서 (requirements.md)](../api/requirements.md)
- [가용성 패턴 (availability-patterns.md)](../api/availability-patterns.md)
- [데이터 모델 (data-models.md)](../api/data-models.md)

---

**Last Updated:** 2025-01-10
**Version:** 2.0
**Author:** Claude Code
**Review Status:** Ready for Week 4 Implementation
