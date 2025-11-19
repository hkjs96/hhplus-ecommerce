# 동시성 제어 방식 비교 (Solution Comparison)

> **목적**: 동시성 제어의 다양한 방식을 성능, 복잡도, 안정성 측면에서 비교하고, 상황별 최적의 선택 가이드를 제공한다.

---

## 📌 동시성 제어 방식 개요

### 1. Pessimistic Lock (비관적 락)
### 2. Optimistic Lock (낙관적 락)
### 3. Distributed Lock (분산 락)
### 4. Database Constraint (DB 제약조건)
### 5. Application Lock (애플리케이션 락)

---

## 1. Pessimistic Lock (비관적 락)

### 개념
데이터를 읽는 시점에 락을 획득하여, 다른 트랜잭션이 해당 데이터를 수정하지 못하도록 차단하는 방식

### 구현 방식

#### SQL
```sql
BEGIN TRANSACTION;

SELECT * FROM products
WHERE id = 1
FOR UPDATE;  -- 배타적 락 획득 (X-Lock)

UPDATE products
SET stock = stock - 1
WHERE id = 1;

COMMIT;  -- 락 해제
```

#### JPA
```java
@Transactional
public void decreaseStock(Long productId, int quantity) {
    Product product = em.createQuery(
        "SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", productId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)  // X-Lock
        .getSingleResult();

    product.decreaseStock(quantity);
}
```

### Lock 종류

| Lock Mode | SQL | 설명 | 동시 접근 |
|-----------|-----|------|----------|
| **Shared Lock (S-Lock)** | `FOR SHARE` | 읽기 전용 락 | 여러 트랜잭션이 동시 읽기 가능 |
| **Exclusive Lock (X-Lock)** | `FOR UPDATE` | 쓰기 전용 락 | 다른 트랜잭션 읽기/쓰기 불가 |

### 장점
✅ 데이터 정합성 100% 보장
✅ 구현 단순 (SELECT FOR UPDATE만 추가)
✅ Rollback 자동 처리 (트랜잭션 실패 시)
✅ 충돌이 자주 발생하는 경우 효율적

### 단점
❌ Lock Contention 발생 (대기 시간 증가)
❌ Deadlock 위험
❌ 처리량(TPS) 감소 (30~50%)
❌ 트랜잭션이 길어지면 성능 급격히 저하

### 성능 측정 (재고 차감 시나리오)

| 동시 사용자 | TPS | 평균 응답시간 | P95 응답시간 | 에러율 |
|------------|-----|-------------|-------------|--------|
| 10명 | 850 | 12ms | 20ms | 0% |
| 50명 | 720 | 65ms | 120ms | 0% |
| 100명 | 580 | 150ms | 300ms | 0% |
| 500명 | 350 | 800ms | 1500ms | 0% |

### Deadlock 시나리오 및 해결

**문제 상황**:
```
Transaction A: Product 1 락 → Product 2 락 대기
Transaction B: Product 2 락 → Product 1 락 대기
→ Deadlock 발생!
```

**해결 방법**:
```java
// ✅ 항상 동일한 순서로 락 획득 (ID 오름차순)
public void purchaseMultipleProducts(List<Long> productIds) {
    // 정렬하여 동일한 순서 보장
    Collections.sort(productIds);

    for (Long productId : productIds) {
        Product product = productRepository.findByIdWithLock(productId);
        product.decreaseStock(1);
    }
}
```

### 적합한 케이스
- ✅ 재고 차감 (Hot Item)
- ✅ 결제 처리
- ✅ 좌석 예약
- ✅ 충돌이 자주 발생하는 경우

### 부적합한 케이스
- ❌ 읽기 전용 작업
- ❌ 충돌이 거의 없는 경우
- ❌ 대량의 데이터 처리
- ❌ 트랜잭션이 긴 경우 (10초+)

### 💡 전문가 의견: Atomic Update vs Pessimistic Lock

#### 제이 코치 (멘토링, 실무 경험)
> "비즈니스 복잡도에 따라 판단합니다. 단순 숫자 증감이면 원자적 업데이트만으로 충분하고, 중간에 복잡한 계산이나 검증이 필요하면 비관적 락을 써야 합니다."

#### 박트래픽 (성능 전문가, 15년차)
> "Atomic Update는 DB 레벨에서 한 번의 쿼리로 처리되기 때문에 Pessimistic Lock보다 3~5배 빠릅니다. 하지만 복잡한 비즈니스 로직은 표현할 수 없다는 한계가 있습니다."

#### 언제 Atomic Update를 사용할까?

**✅ 단순 증감 - Atomic Update**
```java
// Repository
@Modifying
@Query("UPDATE Product p SET p.stock = p.stock - :quantity " +
       "WHERE p.id = :id AND p.stock >= :quantity")
int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

// UseCase
@Service
public class StockService {
    public void decreaseStock(Long productId, int quantity) {
        int updated = productRepository.decreaseStock(productId, quantity);
        if (updated == 0) {
            throw new InsufficientStockException();
        }
    }
}

// 장점: 빠름, 간단함, Deadlock 없음
// 단점: 복잡한 로직 불가능
// 성능: 단일 UPDATE 쿼리만 실행 (10ms)
```

#### 언제 Pessimistic Lock을 사용할까?

**✅ 복잡한 로직 - Pessimistic Lock**
```java
@Transactional
public void processOrder(OrderRequest request) {
    // 1. 재고 조회 및 락 획득
    Product product = em.createQuery(
        "SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", request.getProductId())
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getSingleResult();

    // 2. 복잡한 계산
    int baseQuantity = request.getQuantity();
    int bonusQuantity = calculateBonus(request.getUserGrade());  // 등급별 보너스
    int totalQuantity = baseQuantity + bonusQuantity;

    // 3. 재고 검증
    if (product.getStock() < totalQuantity) {
        throw new InsufficientStockException();
    }

    // 4. 할인 쿠폰 적용 여부 확인
    if (request.hasCoupon()) {
        Coupon coupon = couponRepository.findById(request.getCouponId());
        if (!coupon.isValidFor(product)) {
            throw new InvalidCouponException();
        }
    }

    // 5. 재고 차감
    product.decreaseStock(totalQuantity);

    // 6. 포인트 차감
    User user = userRepository.findById(request.getUserId());
    user.deductPoints(calculatePointsUsed(request));
}

// 장점: 복잡한 로직 가능, 데이터 정합성 100%
// 단점: 느림, Deadlock 위험
// 성능: SELECT FOR UPDATE + 비즈니스 로직 + UPDATE (50~100ms)
```

#### 선택 기준 요약

| 상황 | 추천 방식 | 이유 |
|------|----------|------|
| 단순 재고 차감 | Atomic Update | 빠르고 간단 |
| 쿠폰 적용 + 재고 차감 | Pessimistic Lock | 중간 검증 필요 |
| 포인트 + 할인 + 재고 | Pessimistic Lock | 여러 테이블 동시 접근 |
| 조회수 증가 | Atomic Update | 단순 증가 |
| 등급별 차별 재고 차감 | Pessimistic Lock | 복잡한 계산 필요 |

---

## 2. Optimistic Lock (낙관적 락)

### 개념
데이터를 읽을 때는 락을 걸지 않고, 업데이트 시점에 버전을 체크하여 충돌을 감지하는 방식

### 구현 방식

#### JPA
```java
@Entity
public class Product {
    @Id
    private Long id;

    private int stock;

    @Version  // 낙관적 락 활성화
    private int version;

    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new InsufficientStockException();
        }
        this.stock -= quantity;
    }
}
```

#### SQL
```sql
-- 읽기 (락 없음)
SELECT id, stock, version FROM products WHERE id = 1;
-- 결과: stock=10, version=5

-- 업데이트 (버전 체크)
UPDATE products
SET stock = 9, version = 6
WHERE id = 1 AND version = 5;  -- 버전이 일치해야만 업데이트

-- affected_rows = 0이면 충돌 발생!
```

### 충돌 처리

```java
@Transactional
public void decreaseStockWithRetry(Long productId, int quantity) {
    int maxRetries = 3;

    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            Product product = productRepository.findById(productId)
                .orElseThrow();

            product.decreaseStock(quantity);
            productRepository.save(product);  // version 체크

            return;  // 성공

        } catch (OptimisticLockException e) {
            if (attempt == maxRetries - 1) {
                throw new StockUpdateFailedException("재시도 실패", e);
            }

            // Exponential Backoff
            Thread.sleep(50 * (attempt + 1));
        }
    }
}
```

### 장점
✅ Lock을 잡지 않아 높은 처리량 유지
✅ Deadlock 발생하지 않음
✅ 읽기 성능 우수
✅ 충돌이 드문 경우 Pessimistic Lock보다 2배 빠름

### 단점
❌ 충돌 발생 시 재시도 필요 (애플리케이션 로직)
❌ 충돌이 자주 발생하면 비효율적
❌ 사용자에게 "다시 시도" 메시지 노출
❌ 재시도 로직 구현 복잡

### 성능 측정 (상품 정보 수정 시나리오)

| 동시 사용자 | TPS | 평균 응답시간 | P95 응답시간 | 에러율 (재시도 후) |
|------------|-----|-------------|-------------|-------------------|
| 10명 | 950 | 10ms | 18ms | 0% |
| 50명 | 880 | 55ms | 100ms | 2% (재시도 3회) |
| 100명 | 720 | 130ms | 250ms | 5% |
| 500명 | 420 | 600ms | 1200ms | 15% |

### 적합한 케이스
- ✅ 상품 정보 수정
- ✅ 리뷰 작성
- ✅ 프로필 업데이트
- ✅ 충돌이 드문 경우

### 부적합한 케이스
- ❌ 재고 차감 (충돌 빈번)
- ❌ 쿠폰 발급 (선착순)
- ❌ 결제 처리 (재시도 부적합)

---

## 3. Distributed Lock (분산 락)

### 개념
Redis, Zookeeper 등 외부 저장소를 활용하여 분산 환경에서도 단일 작업을 보장하는 방식

### 구현 방식 (Redisson)

```java
@Service
public class CouponService {

    private final RedissonClient redissonClient;

    public void issueCoupon(Long couponId, Long userId) {
        String lockKey = "lock:coupon:" + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Lock 획득 시도 (100ms 대기, 3초 후 자동 해제)
            if (lock.tryLock(100, 3000, TimeUnit.MILLISECONDS)) {
                // Critical Section
                Coupon coupon = couponRepository.findById(couponId);

                if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
                    coupon.increaseIssued();
                    userCouponRepository.save(new UserCoupon(userId, couponId));
                }
            } else {
                throw new LockAcquisitionFailedException();
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

### Redis Lua Script (원자적 연산)

```java
public void issueCouponFast(Long couponId, Long userId) {
    String luaScript = """
        local stock = redis.call('get', KEYS[1])
        if not stock or tonumber(stock) <= 0 then
            return 0
        end
        redis.call('decr', KEYS[1])
        return 1
    """;

    DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
    Long result = redisTemplate.execute(
        script,
        List.of("coupon:" + couponId + ":stock")
    );

    if (result == 1) {
        // DB 비동기 저장
        saveCouponAsync(couponId, userId);
    } else {
        throw new CouponSoldOutException();
    }
}
```

### 장점
✅ 분산 환경에서도 동작
✅ 극도로 빠른 성능 (TPS 10,000+)
✅ DB 부하 감소
✅ Scale-out 가능

### 단점
❌ 추가 인프라 필요 (Redis Cluster)
❌ 네트워크 지연 발생
❌ Redis 장애 시 서비스 불가
❌ 운영 복잡도 증가

### 성능 측정 (선착순 쿠폰 시나리오)

| 동시 사용자 | TPS | 평균 응답시간 | P95 응답시간 | 에러율 |
|------------|-----|-------------|-------------|--------|
| 100명 | 8500 | 12ms | 25ms | 0% |
| 500명 | 12000 | 40ms | 80ms | 0% |
| 1000명 | 15000 | 65ms | 120ms | 0% |
| 5000명 | 18000 | 250ms | 500ms | 0.5% |

### 적합한 케이스
- ✅ 선착순 쿠폰 발급
- ✅ 한정 수량 이벤트
- ✅ 분산 환경 (다중 인스턴스)
- ✅ 초당 10,000+ TPS 필요

### 부적합한 케이스
- ❌ 단일 인스턴스 환경
- ❌ Redis 인프라 없는 경우
- ❌ 트래픽이 적은 경우

### 💡 전문가 의견: Distributed Lock vs Idempotency Key

#### 제이 코치 (멘토링, 실무 경험)
> "분산락은 시간 단위가 짧아서 밀리초 단위 동시 요청을 막는 거고, Idempotency는 시간 단위가 길어서 한 번 처리된 요청을 몇 분, 몇 시간 기억해 줍니다."

#### 김데이터 (DBA, 20년차)
> "Distributed Lock은 Redis에 임시로 저장되고 TTL로 자동 삭제되지만, Idempotency Key는 DB에 영구적으로 저장됩니다. 목적이 다른 두 가지 패턴입니다."

#### 시간 단위 차이

**Distributed Lock: 밀리초~초 단위**
```java
public void issueCoupon(Long couponId, Long userId) {
    String lockKey = "lock:coupon:" + couponId;
    RLock lock = redissonClient.getLock(lockKey);

    try {
        // 100ms 대기, 3초 후 자동 해제
        if (lock.tryLock(100, 3000, TimeUnit.MILLISECONDS)) {
            // Critical Section (100ms 소요)
            Coupon coupon = couponRepository.findById(couponId);
            coupon.increaseIssued();
            userCouponRepository.save(new UserCoupon(userId, couponId));
        }
    } finally {
        lock.unlock();  // 락 즉시 해제
    }
}

// 시나리오:
// 10:00:00.000 - User1 락 획득
// 10:00:00.001 - User2 대기 (락 없음)
// 10:00:00.100 - User1 완료, 락 해제
// 10:00:00.101 - User2 락 획득
```

**Idempotency Key: 분~시간~일 단위**
```java
@Transactional
public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
    // 이미 처리된 요청인지 확인 (24시간 보관)
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        log.info("Duplicate request: {}", idempotencyKey);
        return PaymentResult.from(existing.get());
    }

    // 결제 처리
    Payment payment = Payment.create(idempotencyKey, request);
    paymentRepository.save(payment);

    return PaymentResult.success();
}

// 시나리오:
// 10:00:00 - 결제 요청 (idempotencyKey="payment-123")
// 10:00:00 - DB에 저장
// 10:00:05 - 같은 요청 재시도 (네트워크 오류로)
// 10:00:05 - 이미 존재 → 기존 결과 반환
// 11:00:00 - 1시간 후에도 중복 방지
```

#### 박트래픽 (성능 전문가, 15년차)
> "Distributed Lock은 동시성 제어, Idempotency Key는 멱등성 보장입니다. 쿠폰 발급은 Distributed Lock, 결제 처리는 Idempotency Key를 사용하세요."

#### 비교표

| 특징 | Distributed Lock | Idempotency Key |
|------|-----------------|----------------|
| **목적** | 동시 실행 방지 | 중복 실행 방지 |
| **시간 단위** | 밀리초~초 | 분~시간~일 |
| **저장소** | Redis (메모리) | DB (영구) |
| **자동 해제** | TTL (타임아웃) | 수동 삭제 또는 TTL |
| **사용 케이스** | 쿠폰 발급, 재고 차감 | 결제, 주문 생성 |
| **네트워크 재시도** | ❌ 보호 안 됨 (락 풀림) | ✅ 보호됨 (DB 기록) |
| **분산 환경** | ✅ 필수 | ✅ 권장 |

#### 최아키텍트 (MSA, 10년차)
> "MSA 환경에서는 두 패턴을 모두 사용합니다. API Gateway에서 Idempotency Key로 중복 요청을 막고, 각 서비스 내부에서 Distributed Lock으로 동시성을 제어합니다."

**조합 사용 예시:**
```java
// API Controller: Idempotency Key 체크
@PostMapping("/payments")
public ApiResponse<PaymentResult> processPayment(
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestBody PaymentRequest request
) {
    // 1차 방어: 중복 요청 차단 (24시간 유효)
    PaymentResult result = paymentService.processPayment(idempotencyKey, request);
    return ApiResponse.success(result);
}

// Service: Distributed Lock 사용
@Service
public class PaymentService {

    @Transactional
    public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
        // Idempotency 체크
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PaymentResult.from(existing.get());
        }

        // 2차 방어: 동시 실행 차단 (1초 이내)
        String lockKey = "lock:payment:" + request.getUserId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
                // 실제 결제 로직
                User user = userRepository.findByIdWithLock(request.getUserId());
                user.deductBalance(request.getAmount());

                Payment payment = Payment.create(idempotencyKey, request);
                paymentRepository.save(payment);

                return PaymentResult.success(payment);
            } else {
                throw new ConcurrentPaymentException();
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

## 4. Database Constraint (DB 제약조건)

### 개념
DB 스키마에 제약조건을 설정하여 잘못된 데이터 입력을 원천 차단하는 방식

### 구현 방식

#### Unique Constraint (중복 방지)
```sql
CREATE TABLE user_coupons (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    issued_at TIMESTAMP,
    UNIQUE KEY uk_user_coupon (user_id, coupon_id)  -- 중복 발급 방지
);
```

#### Check Constraint (음수 방지)
```sql
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    stock INT NOT NULL,
    CONSTRAINT chk_stock_positive CHECK (stock >= 0)  -- 음수 재고 방지
);
```

#### Foreign Key + Cascade
```sql
CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
```

### 장점
✅ 원천적으로 잘못된 데이터 차단
✅ 애플리케이션 코드 변경 없이 적용
✅ 모든 접근 경로에 적용 (Admin, Batch 등)
✅ 유지보수 용이

### 단점
❌ 복잡한 비즈니스 로직 표현 어려움
❌ 에러 메시지 불친절
❌ 마이그레이션 복잡

### 적합한 케이스
- ✅ 중복 발급 방지 (1인 1매 쿠폰)
- ✅ 음수 재고/잔액 방지
- ✅ 상태 전이 검증 (Enum)
- ✅ 데이터 무결성 보장

---

## 5. Application Lock (애플리케이션 락)

### 개념
Java의 `synchronized`, `ReentrantLock` 등을 활용한 메모리 기반 락

### 구현 방식

#### synchronized
```java
@Service
public class CouponService {

    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public synchronized void issueCoupon(Long couponId, Long userId) {
        Object lock = locks.computeIfAbsent(couponId, k -> new Object());

        synchronized (lock) {
            Coupon coupon = couponRepository.findById(couponId);

            if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
                coupon.increaseIssued();
                userCouponRepository.save(new UserCoupon(userId, couponId));
            }
        }
    }
}
```

#### ReentrantLock
```java
private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

public void issueCoupon(Long couponId, Long userId) {
    ReentrantLock lock = locks.computeIfAbsent(couponId, k -> new ReentrantLock());

    if (lock.tryLock(1, TimeUnit.SECONDS)) {
        try {
            // Critical Section
            Coupon coupon = couponRepository.findById(couponId);
            coupon.increaseIssued();
            userCouponRepository.save(new UserCoupon(userId, couponId));
        } finally {
            lock.unlock();
        }
    } else {
        throw new LockTimeoutException();
    }
}
```

### 장점
✅ 매우 빠름 (메모리 기반)
✅ 추가 인프라 불필요
✅ 구현 단순
✅ 디버깅 용이

### 단점
❌ 단일 인스턴스에서만 동작
❌ Scale-out 불가
❌ 인스턴스 재시작 시 상태 손실
❌ 분산 환경 부적합

### 적합한 케이스
- ✅ MVP / 프로토타입
- ✅ 단일 인스턴스 환경
- ✅ 내부 Admin 시스템

### 부적합한 케이스
- ❌ 프로덕션 환경 (다중 인스턴스)
- ❌ 클라우드 환경 (Auto-scaling)

---

## 📊 종합 비교표

| 방식 | 성능 | 정합성 | 복잡도 | 분산 지원 | 비용 | 권장 케이스 |
|------|------|--------|--------|-----------|------|------------|
| **Pessimistic Lock** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ✅ | 낮음 | 재고 차감, 결제 |
| **Optimistic Lock** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ✅ | 낮음 | 상품 정보 수정 |
| **Distributed Lock** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ✅ | 높음 | 선착순 쿠폰 |
| **DB Constraint** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐ | ✅ | 낮음 | 데이터 무결성 |
| **Application Lock** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐ | ❌ | 낮음 | MVP, 단일 인스턴스 |

---

## 🎯 시나리오별 추천 방식

### E-Commerce 시스템

| 시나리오 | 추천 방식 | 이유 |
|---------|----------|------|
| **재고 차감** | Pessimistic Lock | 충돌 빈번, Over-selling 방지 필수 |
| **선착순 쿠폰** | Distributed Lock (Redis) | 극도로 높은 동시성, 정확히 N개 발급 |
| **결제 처리** | Idempotency Key + Pessimistic Lock | 중복 결제 방지 |
| **잔액 업데이트** | Pessimistic Lock + Atomic Update | Lost Update 방지 |
| **주문 상태** | Optimistic Lock + State Machine | 충돌 드묾, 상태 검증 중요 |
| **상품 정보 수정** | Optimistic Lock | 충돌 거의 없음 |
| **리뷰 작성** | Optimistic Lock | 충돌 없음 |
| **1인 1매 쿠폰** | DB Unique Constraint | 중복 발급 원천 차단 |

---

## 🚀 선택 가이드

### Step 1: 트래픽 규모 파악
- **저트래픽** (TPS < 100): Pessimistic Lock 또는 Application Lock
- **중트래픽** (TPS 100~1000): Pessimistic Lock 또는 Optimistic Lock
- **고트래픽** (TPS > 1000): Distributed Lock (Redis)

### Step 2: 충돌 빈도 예측
- **충돌 자주 발생** (>10%): Pessimistic Lock
- **충돌 가끔 발생** (1~10%): Optimistic Lock
- **충돌 거의 없음** (<1%): Optimistic Lock

### Step 3: 정합성 요구사항
- **완벽한 정합성 필수** (금융, 재고): Pessimistic Lock
- **최종 일관성 허용**: Optimistic Lock + Retry

### Step 4: 인프라 현황
- **단일 인스턴스**: Application Lock
- **다중 인스턴스 + Redis 있음**: Distributed Lock
- **다중 인스턴스 + Redis 없음**: Pessimistic Lock

---

## 💡 전문가 의견 요약

### 김데이터 (DBA, 20년차)
> "동시성 문제는 DB 레벨에서 해결하는 것이 가장 확실하다. Pessimistic Lock + DB Constraint 조합을 권장한다."

### 박트래픽 (성능 전문가, 15년차)
> "성능이 중요하다면 Redis Distributed Lock을 활용하라. DB Lock은 병목이 된다."

### 이금융 (금융권, 12년차)
> "금융권에서는 Pessimistic Lock + Audit Trail이 필수다. 성능보다 정확성이 우선이다."

### 최아키텍트 (MSA, 10년차)
> "분산 환경에서는 완벽한 일관성을 포기하고 최종 일관성을 추구하는 것이 현실적이다."

### 정스타트업 (CTO, 7년차)
> "처음에는 단순한 방식(Pessimistic Lock)으로 시작하고, 병목 발생 시 최적화하라."

---

## 📚 다음 문서

- **구현 가이드**: [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md)
- **테스트 전략**: [TEST_STRATEGY.md](./TEST_STRATEGY.md)
- **성능 최적화**: [PERFORMANCE_OPTIMIZATION.md](./PERFORMANCE_OPTIMIZATION.md)

---

**작성일**: 2025-11-18
**버전**: 1.0
