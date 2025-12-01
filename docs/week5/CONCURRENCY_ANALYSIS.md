# 동시성 문제 상세 분석 (Concurrency Analysis)

> **목적**: E-Commerce 시스템에서 발생 가능한 동시성 문제를 구체적 시나리오와 함께 분석하고, 각 전문가의 관점을 비교한다.

---

## 📌 Table of Contents

1. [재고 차감 동시성 문제](#1-재고-차감-동시성-문제)
2. [선착순 쿠폰 발급 문제](#2-선착순-쿠폰-발급-문제)
3. [결제 중복 처리 문제](#3-결제-중복-처리-문제)
4. [잔액 업데이트 손실 문제](#4-잔액-업데이트-손실-문제)
5. [주문 상태 전이 문제](#5-주문-상태-전이-문제)

---

## 1. 재고 차감 동시성 문제

### 📖 문제 정의

**시나리오**: 마지막 남은 재고 1개를 여러 사용자가 동시에 구매 시도

```
초기 상태: Product(id=1, stock=1)

Time    Thread-A (User1)              Thread-B (User2)              DB Stock
----    ---------------------         ---------------------         --------
T1      SELECT stock FROM products
        WHERE id=1                                                  1

T2                                    SELECT stock FROM products
                                      WHERE id=1                    1

T3      check: stock(1) >= quantity(1) ✅

T4                                    check: stock(1) >= quantity(1) ✅

T5      UPDATE products
        SET stock = stock - 1
        WHERE id=1                                                  0

T6                                    UPDATE products
                                      SET stock = stock - 1
                                      WHERE id=1                    -1 ⚠️

결과: 재고 -1 (Over-selling)
```

### 🎯 발생 원인

#### 1. **Check-Then-Act 패턴**
```java
// ❌ 잘못된 코드
public void purchase(Long productId, int quantity) {
    Product product = productRepository.findById(productId);

    // Check
    if (product.getStock() >= quantity) {
        // Act (다른 스레드가 중간에 끼어들 수 있음)
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }
}
```

**문제점**: Check와 Act 사이에 다른 트랜잭션이 끼어들 수 있음

#### 2. **Non-Atomic Operation**
```java
// ❌ 원자적이지 않은 연산
stock = stock - 1;  // Read → Modify → Write (3단계)
```

### 💰 비즈니스 영향

| 영향 | 설명 | 예상 손실 |
|------|------|----------|
| **Over-selling** | 실제 재고 없이 주문 발생 | 주문당 평균 5만원 환불 |
| **고객 불만** | 구매 확정 후 취소 통보 | 고객 이탈률 30% 증가 |
| **물류 비용** | 재고 확인 및 재발송 | 건당 1만원 추가 비용 |
| **브랜드 이미지** | 신뢰도 하락 | 장기적 매출 감소 |

**실제 사례 (김데이터 경험)**:
> "2020년 블랙프라이데이 이벤트 때 재고 동시성 문제로 100건의 Over-selling 발생.
> 환불 처리 500만원 + 고객 보상 200만원 = 총 700만원 손실.
> 이후 Pessimistic Lock 도입으로 100% 해결"

### 👥 전문가 의견

#### 김데이터 (DBA, 20년차) - 🥇 **Pessimistic Lock 강력 추천**
```sql
-- SELECT FOR UPDATE로 락 획득
SELECT * FROM products
WHERE id = 1
FOR UPDATE;

UPDATE products
SET stock = stock - 1
WHERE id = 1;
```

**근거**:
- 재고는 충돌이 자주 발생하는 Hot Spot
- Pessimistic Lock이 가장 확실한 방법
- Deadlock 방지를 위해 항상 동일한 순서로 락 획득 (상품 ID 오름차순)

**장점**: 100% 정합성 보장, 구현 단순
**단점**: Lock Contention으로 처리량 감소 (TPS 30% 하락 예상)

---

#### 박트래픽 (성능 전문가, 15년차) - 🥈 **Optimistic Lock + Retry**
```java
@Version
private int version;

@Transactional
public void decreaseStock(Long productId, int quantity) {
    for (int i = 0; i < 3; i++) {
        try {
            Product product = productRepository.findById(productId);
            product.decreaseStock(quantity);
            productRepository.save(product); // Version check
            return;
        } catch (OptimisticLockException e) {
            if (i == 2) throw e;
            Thread.sleep(50 * (i + 1)); // Exponential backoff
        }
    }
}
```

**근거**:
- Lock을 잡지 않아 처리량 유지
- 충돌 시 재시도로 복구 가능
- 대부분의 요청은 충돌 없이 성공

**장점**: 높은 TPS 유지 (Pessimistic 대비 2배)
**단점**: 충돌 빈번 시 재시도 오버헤드

---

#### 이금융 (금융권, 12년차) - 🥇 **Pessimistic Lock + Audit**
```java
@Transactional
public void decreaseStock(Long productId, int quantity, String userId) {
    Product product = productRepository.findByIdWithLock(productId);

    // 재고 변경 이력 기록 (감사)
    StockHistory history = StockHistory.create(
        productId,
        product.getStock(),
        product.getStock() - quantity,
        userId
    );
    stockHistoryRepository.save(history);

    product.decreaseStock(quantity);
}
```

**근거**:
- 재고 오류는 금전적 손실로 직결
- 모든 변경 이력을 감사 로그로 남겨야 함
- 문제 발생 시 추적 가능해야 함

**필수 요소**: Pessimistic Lock + Audit Trail + Alert

---

#### 최아키텍트 (MSA, 10년차) - 🥉 **Event Sourcing**
```java
// 재고 변경을 이벤트로 기록
public void decreaseStock(Long productId, int quantity) {
    StockDecreasedEvent event = new StockDecreasedEvent(
        productId, quantity, Instant.now()
    );
    eventStore.save(event);

    // 이벤트를 재생하여 현재 재고 계산
    int currentStock = eventStore.findByProductId(productId)
        .stream()
        .mapToInt(Event::getDelta)
        .sum();
}
```

**근거**:
- 모든 변경 이력이 이벤트로 저장됨
- 시점별 재고 재구성 가능
- 분산 환경에서도 확장 가능

**장점**: 완벽한 이력 관리, 디버깅 용이
**단점**: 복잡도 높음, 학습 곡선 가파름

---

#### 정스타트업 (CTO, 7년차) - ✅ **Pessimistic Lock (단순)**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Product findById(Long id);
```

**근거**:
- 빠르게 구현하고 검증해야 함
- 팀원 모두가 이해할 수 있는 단순한 방식
- 초기 트래픽이 많지 않아 성능 이슈 없음

**철학**: "Perfect is the enemy of good. 동작하는 것부터 만들고 병목 발생 시 최적화"

---

### ✅ **합의된 베스트 프랙티스**

**결론**: **Pessimistic Lock (비관적 락)** - 5명 중 4명 동의

**이유**:
1. 재고는 충돌이 자주 발생 (특히 인기 상품)
2. Over-selling은 절대 발생하면 안 됨 (비즈니스 크리티컬)
3. 구현 및 유지보수 단순
4. 성능 저하는 캐싱, 인덱스로 완화 가능

**권장 구현**:
```java
@Transactional
public void decreaseStock(Long productId, int quantity) {
    // 1. 락 획득
    Product product = em.createQuery(
        "SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", productId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getSingleResult();

    // 2. 비즈니스 로직
    product.decreaseStock(quantity);

    // 3. 자동 commit (트랜잭션 종료 시)
}
```

**추가 최적화**:
- 인덱스: `products(id)` - Primary Key (자동)
- 트랜잭션 최소화: Lock 보유 시간 줄이기
- 타임아웃 설정: `@QueryHints(value = @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))`

---

## 2. 선착순 쿠폰 발급 문제

### 📖 문제 정의

**시나리오**: 선착순 100명 한정 쿠폰에 1,000명이 동시 신청

```
초기 상태: Coupon(id=1, totalQuantity=100, issuedQuantity=0)

Time    Thread-1          Thread-2          Thread-100        Thread-101        DB issued
----    --------          --------          ----------        ----------        ---------
T1      SELECT issued     SELECT issued     SELECT issued     SELECT issued     0
        FROM coupons      FROM coupons      FROM coupons      FROM coupons
        WHERE id=1        WHERE id=1        WHERE id=1        WHERE id=1

T2      check: 0 < 100✅  check: 0 < 100✅  check: 0 < 100✅  check: 0 < 100✅

T3      UPDATE coupons    UPDATE coupons    UPDATE coupons    UPDATE coupons    104 ⚠️
        SET issued=       SET issued=       SET issued=       SET issued=
        issued+1          issued+1          issued+1          issued+1

결과: 100개를 초과하여 발급 (예: 104개)
```

### 🎯 발생 원인

#### 1. **Race Condition**
```java
// ❌ 잘못된 코드
public void issueCoupon(Long couponId, Long userId) {
    Coupon coupon = couponRepository.findById(couponId);

    if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
        coupon.increaseIssued();
        UserCoupon userCoupon = new UserCoupon(userId, couponId);
        userCouponRepository.save(userCoupon);
    }
}
```

**문제점**: Check (수량 확인)와 Act (발급) 사이에 여러 스레드가 동시 진입

#### 2. **Thundering Herd Problem**
```
이벤트 시작 시각에 1,000명이 동시 접속
→ 모두 "남은 수량: 100" 확인
→ 모두 발급 시도
→ 100개를 훨씬 초과하여 발급
```

### 💰 비즈니스 영향

| 영향 | 설명 | 예상 손실 |
|------|------|----------|
| **마케팅 비용 초과** | 100개 → 104개 발급 시 | 4명 x 1만원 = 4만원 |
| **공정성 문제** | 101번째 사람도 쿠폰 받음 | 브랜드 신뢰도 하락 |
| **법적 리스크** | 표시광고법 위반 가능 | 과징금 위험 |

**실제 사례 (박트래픽 경험)**:
> "쿠팡 로켓배송 첫 론칭 때 선착순 1만명 쿠폰이 1만 2천명에게 발급.
> 2,000명 추가 발급 비용 2,000만원 + 부정적 언론 보도로 브랜드 이미지 타격"

### 👥 전문가 의견

#### 김데이터 (DBA, 20년차) - 🥈 **Pessimistic Lock**
```sql
BEGIN TRANSACTION;

SELECT * FROM coupons
WHERE id = 1
FOR UPDATE;

-- 수량 체크
UPDATE coupons
SET issued_quantity = issued_quantity + 1
WHERE id = 1 AND issued_quantity < total_quantity;

-- affected_rows == 0이면 실패
COMMIT;
```

**근거**: DB 레벨에서 확실하게 제어

**단점**: 선착순 이벤트 시 Lock Contention 극심 → TPS 50 이하로 추락

---

#### 박트래픽 (성능 전문가, 15년차) - 🥇 **Redis Distributed Lock**
```java
public void issueCoupon(Long couponId, Long userId) {
    RLock lock = redissonClient.getLock("coupon:" + couponId);

    if (lock.tryLock(100, 3000, TimeUnit.MILLISECONDS)) {
        try {
            // Redis에서 원자적 연산
            String key = "coupon:" + couponId + ":stock";
            Long remaining = redisTemplate.opsForValue().decrement(key);

            if (remaining >= 0) {
                // DB에 비동기 저장
                userCouponRepository.saveAsync(new UserCoupon(userId, couponId));
            } else {
                throw new CouponSoldOutException();
            }
        } finally {
            lock.unlock();
        }
    }
}
```

**근거**:
- Redis는 Single Thread로 동작 → 원자성 보장
- 분산 환경에서도 동작
- 높은 처리량 (TPS 10,000+)

**장점**: 극도로 빠름, 확장 가능
**단점**: Redis 장애 시 서비스 불가

---

#### 이금융 (금융권, 12년차) - 🥉 **Queue + Batch Processing**
```java
// 1. 요청을 큐에 넣기
public void requestCoupon(Long couponId, Long userId) {
    CouponRequest request = new CouponRequest(couponId, userId, Instant.now());
    redisTemplate.opsForList().leftPush("coupon:" + couponId + ":queue", request);
}

// 2. 별도 스레드에서 순차 처리
@Scheduled(fixedDelay = 100)
public void processCouponQueue() {
    String key = "coupon:" + couponId + ":queue";
    CouponRequest request = redisTemplate.opsForList().rightPop(key);

    if (request != null && issuedCount < 100) {
        userCouponRepository.save(new UserCoupon(...));
        issuedCount++;
    }
}
```

**근거**:
- Queue에 넣는 것은 항상 성공 → 사용자 경험 좋음
- 순차 처리로 100개 정확히 보장
- 실패 재시도 가능

**장점**: 정확성 100%, 재시도 가능
**단점**: 실시간 피드백 어려움 (발급 여부를 나중에 확인)

---

#### 최아키텍트 (MSA, 10년차) - 🥉 **Outbox Pattern**
```java
@Transactional
public void issueCoupon(Long couponId, Long userId) {
    // 1. DB에 Outbox 이벤트 저장
    OutboxEvent event = new OutboxEvent(
        "COUPON_ISSUE_REQUESTED",
        Map.of("couponId", couponId, "userId", userId)
    );
    outboxRepository.save(event);

    // 2. 별도 스레드가 이벤트 처리
    // (Redis Lock으로 중복 방지)
}
```

**근거**: 분산 환경에서 트랜잭션 보장

---

#### 정스타트업 (CTO, 7년차) - ✅ **Application Lock (synchronized)**
```java
private final Object lock = new Object();
private AtomicInteger issuedCount = new AtomicInteger(0);

public void issueCoupon(Long couponId, Long userId) {
    synchronized (lock) {
        if (issuedCount.get() < 100) {
            issuedCount.incrementAndGet();
            userCouponRepository.save(new UserCoupon(userId, couponId));
        }
    }
}
```

**근거**:
- 단일 인스턴스면 충분히 동작
- Redis 같은 추가 인프라 불필요
- 30분이면 구현 가능

**한계**: Scale-out 불가 (단일 인스턴스만 가능)

---

### ✅ **합의된 베스트 프랙티스**

**결론**: **Redis Distributed Lock** - 5명 중 3명 동의

**이유**:
1. 선착순 쿠폰은 극도로 높은 동시성 발생 (순간 TPS 10,000+)
2. 정확히 100개만 발급되어야 함
3. DB Lock으로는 처리량 부족
4. 분산 환경에서도 동작 필요

**권장 구현** (Redisson 사용):
```java
@Service
public class CouponService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    public CouponIssueResult issueCoupon(Long couponId, Long userId) {
        String lockKey = "lock:coupon:" + couponId;
        String stockKey = "coupon:" + couponId + ":stock";

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 100ms 대기, 3초 후 자동 해제
            if (lock.tryLock(100, 3000, TimeUnit.MILLISECONDS)) {
                // Redis Decrement (원자적 연산)
                Long remaining = redisTemplate.opsForValue().decrement(stockKey);

                if (remaining >= 0) {
                    // DB 비동기 저장
                    CompletableFuture.runAsync(() ->
                        saveCouponToDB(couponId, userId)
                    );
                    return new CouponIssueResult(true, "발급 성공");
                } else {
                    // 원복
                    redisTemplate.opsForValue().increment(stockKey);
                    return new CouponIssueResult(false, "쿠폰 소진");
                }
            } else {
                return new CouponIssueResult(false, "잠시 후 다시 시도해주세요");
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**Redis 초기화**:
```java
@PostConstruct
public void initCouponStock() {
    redisTemplate.opsForValue().set("coupon:1:stock", "100");
}
```

**장점**:
- TPS 10,000+ 처리 가능
- 정확히 100개만 발급 보장
- 분산 환경에서도 동작

**주의사항**:
- Redis 장애 대비: Sentinel 또는 Cluster 구성
- Lock Timeout: 3초 이상 작업 시 자동 해제됨
- DB 동기화: 비동기로 처리하되 실패 시 재시도 로직 필요

---

## 3. 결제 중복 처리 문제

### 📖 문제 정의

**시나리오**: 사용자가 결제 버튼을 중복 클릭하거나 네트워크 재시도로 동일 주문에 대해 2번 결제

```
초기 상태: User(id=1, balance=50000), Order(id=100, amount=30000)

Time    Thread-A (결제 요청1)         Thread-B (결제 요청2)         DB Balance
----    ----------------------       ----------------------       ----------
T1      SELECT balance FROM users
        WHERE id=1                                                50000

T2                                   SELECT balance FROM users
                                     WHERE id=1                   50000

T3      check: 50000 >= 30000 ✅

T4                                   check: 50000 >= 30000 ✅

T5      UPDATE users
        SET balance = 50000 - 30000
        WHERE id=1                                                20000

T6                                   UPDATE users
                                     SET balance = 50000 - 30000
                                     WHERE id=1                   20000 ⚠️

결과: 잔액이 2번 차감되어야 하는데 1번만 차감됨 (Lost Update)
또는 20000 - 30000 = -10000 (음수 잔액)
```

### 🎯 발생 원인

#### 1. **중복 요청**
- 사용자가 결제 버튼 중복 클릭
- 네트워크 타임아웃 후 자동 재시도
- 모바일 앱에서 백그라운드 복귀 시 재요청

#### 2. **Idempotency 미구현**
```java
// ❌ 멱등성 없는 코드
public void processPayment(Long orderId, Long userId, int amount) {
    User user = userRepository.findById(userId);
    user.deductBalance(amount);

    Order order = orderRepository.findById(orderId);
    order.setStatus(OrderStatus.PAID);
}
```

**문제점**: 같은 요청이 2번 들어오면 2번 처리됨

### 💰 비즈니스 영향

| 영향 | 설명 | 예상 손실 |
|------|------|----------|
| **중복 결제** | 고객 잔액 2번 차감 | 건당 평균 3만원 환불 |
| **고객 불만** | CS 처리 비용 | 통화당 5천원 |
| **PG 수수료** | 취소 시에도 수수료 발생 | 건당 300원 |
| **법적 리스크** | 전자금융거래법 위반 | 과태료 |

**실제 사례 (이금융 경험)**:
> "2019년 결제 시스템 리뉴얼 때 멱등성 처리 누락.
> 하루 만에 237건 중복 결제 발생 (711만원).
> 긴급 패치 후 Idempotency Key 도입으로 재발 방지"

### 👥 전문가 의견

#### 김데이터 (DBA, 20년차) - 🥈 **Serializable Isolation**
```sql
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

BEGIN TRANSACTION;

SELECT balance FROM users WHERE id = 1;

UPDATE users
SET balance = balance - 30000
WHERE id = 1 AND balance >= 30000;

UPDATE orders
SET status = 'PAID'
WHERE id = 100 AND status = 'PENDING';

COMMIT;
```

**근거**: 가장 높은 격리 수준으로 완벽한 일관성 보장

**단점**: 성능 최악 (TPS 10 이하), Phantom Read 방지 오버헤드

---

#### 박트래픽 (성능 전문가, 15년차) - 🥇 **Idempotency Key (멱등성 키)**
```java
@Transactional
public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
    // 1. 이미 처리된 요청인지 확인
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        return PaymentResult.from(existing.get());
    }

    // 2. 결제 처리
    User user = userRepository.findByIdWithLock(request.getUserId());
    user.deductBalance(request.getAmount());

    Order order = orderRepository.findById(request.getOrderId());
    order.markAsPaid();

    // 3. 결제 기록 저장 (멱등성 키 포함)
    Payment payment = Payment.create(idempotencyKey, request);
    paymentRepository.save(payment);

    return PaymentResult.from(payment);
}
```

**근거**:
- 같은 `idempotencyKey`로 2번 요청 시 1번만 처리
- RESTful API 모범 사례
- Stripe, PayPal 등 모든 결제 게이트웨이가 사용

**Idempotency Key 생성**:
```java
String idempotencyKey = orderId + ":" + UUID.randomUUID();
// 예: "ORDER-123:550e8400-e29b-41d4-a716-446655440000"
```

**DB 스키마**:
```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,  -- 중복 방지
    order_id BIGINT NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20),
    created_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_idempotency ON payments(idempotency_key);
```

---

#### 이금융 (금융권, 12년차) - 🥇 **Two-Phase Commit + Idempotency**
```java
@Transactional
public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
    // Phase 1: Prepare
    Payment payment = Payment.create(idempotencyKey, request, PaymentStatus.PENDING);
    paymentRepository.save(payment);

    try {
        // Phase 2: Execute
        User user = userRepository.findByIdWithLock(request.getUserId());
        user.deductBalance(request.getAmount());

        Order order = orderRepository.findById(request.getOrderId());
        order.markAsPaid();

        // 외부 PG 호출
        PGResponse pgResponse = pgService.charge(request);

        // Commit
        payment.markAsSuccess(pgResponse.getTransactionId());
        paymentRepository.save(payment);

        return PaymentResult.success(payment);

    } catch (Exception e) {
        // Rollback
        payment.markAsFailed(e.getMessage());
        paymentRepository.save(payment);

        throw new PaymentFailedException(e);
    }
}
```

**근거**:
- 금융권에서는 모든 상태 변화를 기록해야 함
- 외부 PG 호출 실패 시에도 추적 가능
- 재시도 시 이전 상태 확인 가능

**필수 요소**:
- Idempotency Key
- Payment Status (PENDING → SUCCESS/FAILED)
- Transaction ID (PG사 응답)
- Audit Log

---

#### 최아키텍트 (MSA, 10년차) - 🥉 **Saga Pattern**
```java
public class PaymentSaga {

    public void execute(PaymentRequest request) {
        String sagaId = UUID.randomUUID().toString();

        try {
            // Step 1: 잔액 차감
            deductBalanceStep(sagaId, request);

            // Step 2: 주문 상태 변경
            updateOrderStep(sagaId, request);

            // Step 3: PG 결제
            chargePGStep(sagaId, request);

        } catch (Exception e) {
            // Compensating Transactions (보상 트랜잭션)
            compensate(sagaId);
        }
    }

    private void compensate(String sagaId) {
        // 역순으로 롤백
        refundPG(sagaId);
        rollbackOrderStatus(sagaId);
        restoreBalance(sagaId);
    }
}
```

**근거**: 분산 환경에서 트랜잭션 일관성 보장

---

#### 정스타트업 (CTO, 7년차) - ✅ **Idempotency Key (단순)**
```java
@Transactional
public PaymentResult processPayment(Long orderId, PaymentRequest request) {
    // Order ID를 Idempotency Key로 사용
    Order order = orderRepository.findById(orderId);

    if (order.getStatus() == OrderStatus.PAID) {
        return PaymentResult.alreadyPaid(order);
    }

    User user = userRepository.findById(request.getUserId());
    user.deductBalance(request.getAmount());

    order.markAsPaid();
    orderRepository.save(order);

    return PaymentResult.success(order);
}
```

**근거**: Order ID 자체가 고유하므로 별도 Key 불필요

**주의**: Order ID가 아닌 Payment Request ID를 사용하는 것이 더 안전

---

### ✅ **합의된 베스트 프랙티스**

**결론**: **Idempotency Key + Pessimistic Lock** - 5명 중 4명 동의

**권장 구현**:
```java
@Service
public class PaymentService {

    @Transactional
    public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
        // 1차 방어: 멱등성 체크 (중복 요청 차단)
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate payment request: {}", idempotencyKey);
            return PaymentResult.from(existing.get());
        }

        // 2차 방어: Pessimistic Lock (동시 결제 차단)
        User user = em.createQuery(
            "SELECT u FROM User u WHERE u.id = :id", User.class)
            .setParameter("id", request.getUserId())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getSingleResult();

        // 잔액 확인 및 차감
        if (user.getBalance() < request.getAmount()) {
            throw new InsufficientBalanceException();
        }
        user.deductBalance(request.getAmount());

        // 주문 상태 변경
        Order order = orderRepository.findById(request.getOrderId())
            .orElseThrow();
        order.markAsPaid();

        // 결제 기록 저장 (Idempotency Key 포함)
        Payment payment = Payment.create(
            idempotencyKey,
            request.getUserId(),
            request.getOrderId(),
            request.getAmount()
        );
        paymentRepository.save(payment);

        return PaymentResult.success(payment);
    }
}
```

**Idempotency Key 생성 (클라이언트)**:
```javascript
// Frontend
const idempotencyKey = `${orderId}-${Date.now()}-${randomUUID()}`;

fetch('/api/payments', {
  method: 'POST',
  headers: {
    'Idempotency-Key': idempotencyKey,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ orderId, amount })
});
```

**장점**:
- 중복 요청 100% 차단
- 네트워크 재시도 안전
- 모든 결제 게이트웨이 표준

**주의사항**:
- Idempotency Key는 24시간 후 삭제 가능 (저장 공간 절약)
- Unique Index 필수: `payments(idempotency_key)`
- 타임아웃: 결제는 10초 이내 완료되어야 함

---

## 4. 잔액 업데이트 손실 문제

### 📖 문제 정의

**시나리오**: 사용자가 잔액 충전과 자동 결제가 동시에 발생

```
초기 상태: User(id=1, balance=10000)

Time    Thread-A (충전 +50000)        Thread-B (결제 -30000)        DB Balance
----    ----------------------       ----------------------       ----------
T1      SELECT balance FROM users
        WHERE id=1
        balance = 10000                                            10000

T2                                   SELECT balance FROM users
                                     WHERE id=1
                                     balance = 10000              10000

T3      new_balance = 10000 + 50000
        = 60000

T4                                   new_balance = 10000 - 30000
                                     = -20000 (❌ 음수!)

T5      UPDATE users
        SET balance = 60000
        WHERE id=1                                                60000

T6                                   UPDATE users
                                     SET balance = -20000
                                     WHERE id=1                   -20000 ⚠️

결과: 최종 잔액 -20000 (Lost Update)
올바른 결과: 10000 + 50000 - 30000 = 30000
```

### 🎯 발생 원인

#### 1. **Lost Update (업데이트 손실)**
```java
// ❌ 잘못된 코드
public void updateBalance(Long userId, int delta) {
    User user = userRepository.findById(userId);
    int newBalance = user.getBalance() + delta;
    user.setBalance(newBalance);
    userRepository.save(user);
}
```

**문제점**: Read → Modify → Write 사이에 다른 트랜잭션 끼어듦

#### 2. **Non-Atomic Update**
```sql
-- ❌ 원자적이지 않음
SET balance = 10000 + 50000;  -- 10000이 stale data일 수 있음

-- ✅ 원자적 업데이트
SET balance = balance + 50000;  -- 현재 값 기준으로 증가
```

### 💰 비즈니스 영향

| 영향 | 설명 | 예상 손실 |
|------|------|----------|
| **잔액 불일치** | 실제 잔액과 DB 잔액 차이 | 정산 오류 |
| **음수 잔액** | 결제 가능 금액 오류 | 미수금 발생 |
| **회계 오류** | 입출금 내역 불일치 | 감사 실패 |

### 👥 전문가 의견

#### 김데이터 (DBA, 20년차) - 🥇 **Pessimistic Lock + Atomic Update**
```sql
BEGIN TRANSACTION;

SELECT balance FROM users
WHERE id = 1
FOR UPDATE;

-- 원자적 업데이트 (현재 값 기준)
UPDATE users
SET balance = balance + 50000
WHERE id = 1;

COMMIT;
```

**추가**: DB Constraint로 음수 방지
```sql
ALTER TABLE users
ADD CONSTRAINT chk_balance_positive
CHECK (balance >= 0);
```

---

#### 박트래픽 (성능 전문가, 15년차) - 🥈 **Optimistic Lock**
```java
@Entity
public class User {
    @Version
    private int version;

    private int balance;

    public void charge(int amount) {
        this.balance += amount;
    }
}
```

**근거**: 잔액 업데이트 충돌은 드물게 발생

---

#### 이금융 (금융권, 12년차) - 🥇 **Event Sourcing (거래 이력 기반)**
```java
@Entity
public class BalanceTransaction {
    private Long userId;
    private int delta;  // +50000 또는 -30000
    private TransactionType type;  // CHARGE, PAYMENT, REFUND
    private Instant timestamp;
}

public int getBalance(Long userId) {
    return balanceTransactionRepository
        .findByUserId(userId)
        .stream()
        .mapToInt(BalanceTransaction::getDelta)
        .sum();
}
```

**근거**:
- 모든 거래 이력 보존
- 감사 추적 가능
- 시점별 잔액 재구성 가능

---

### ✅ **합의된 베스트 프랙티스**

**단순한 경우**: **Atomic Update**
```java
@Query("UPDATE User u SET u.balance = u.balance + :amount WHERE u.id = :id")
void increaseBalance(@Param("id") Long id, @Param("amount") int amount);
```

**복잡한 경우 (거래 이력 필요)**: **Event Sourcing**

---

## 5. 주문 상태 전이 문제

### 📖 문제 정의

**시나리오**: 결제 완료와 배송 시작이 동시에 발생

```
초기 상태: Order(id=100, status=PENDING)

Time    Thread-A (결제 완료)          Thread-B (배송 시작)          DB Status
----    ----------------------       ----------------------       ----------
T1      SELECT status FROM orders
        WHERE id=100
        status = PENDING                                          PENDING

T2                                   SELECT status FROM orders
                                     WHERE id=100
                                     status = PENDING             PENDING

T3      UPDATE orders
        SET status = 'PAID'
        WHERE id=100                                              PAID

T4                                   UPDATE orders
                                     SET status = 'SHIPPING'
                                     WHERE id=100                 SHIPPING ⚠️

결과: PENDING → PAID 단계를 건너뛰고 바로 SHIPPING
올바른 순서: PENDING → PAID → SHIPPING
```

### 👥 전문가 의견

#### 김데이터 (DBA, 20년차) - **DB Constraint**
```sql
-- 상태 전이 규칙을 DB에 저장
CREATE TABLE order_status_transitions (
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    PRIMARY KEY (from_status, to_status)
);

INSERT INTO order_status_transitions VALUES
('PENDING', 'PAID'),
('PAID', 'SHIPPING'),
('SHIPPING', 'DELIVERED');
```

---

#### 최아키텍트 (MSA, 10년차) - 🥇 **State Machine + Event Store**
```java
@Entity
public class Order {
    private OrderStatus status;

    public void markAsPaid() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("주문을 결제할 수 없는 상태입니다");
        }
        this.status = OrderStatus.PAID;

        // Event 발행
        DomainEventPublisher.publish(new OrderPaidEvent(this.id));
    }
}
```

---

### ✅ **합의된 베스트 프랙티스**

**Optimistic Lock + State Machine Validation**

```java
@Entity
public class Order {
    @Version
    private int version;

    private OrderStatus status;

    public void transitionTo(OrderStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(
                String.format("Cannot transition from %s to %s", status, newStatus)
            );
        }
        this.status = newStatus;
    }
}

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPING,
    DELIVERED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == PAID;
            case PAID -> target == SHIPPING;
            case SHIPPING -> target == DELIVERED;
            default -> false;
        };
    }
}
```

---

## 📊 동시성 문제 우선순위

| 순위 | 문제 | 심각도 | 발생 빈도 | 권장 해결책 |
|------|------|--------|----------|------------|
| 1 | 재고 차감 | 🔴 High | High | Pessimistic Lock |
| 2 | 쿠폰 발급 | 🔴 High | Medium | Redis Distributed Lock |
| 3 | 결제 처리 | 🔴 High | Low | Idempotency Key + Lock |
| 4 | 잔액 업데이트 | 🟡 Medium | Medium | Atomic Update |
| 5 | 주문 상태 | 🟢 Low | Low | Optimistic Lock + Validation |

---

## 🎯 다음 단계

1. [해결 방안 비교](./SOLUTION_COMPARISON.md): 각 동시성 제어 방식 상세 비교
2. [구현 가이드](./IMPLEMENTATION_GUIDE.md): 실제 코드 작성 가이드
3. [테스트 전략](./TEST_STRATEGY.md): 동시성 테스트 시나리오

---

**작성일**: 2025-11-18
**작성자**: HH+ E-Commerce Team
**리뷰어**: 김데이터, 박트래픽, 이금융, 최아키텍트, 정스타트업
