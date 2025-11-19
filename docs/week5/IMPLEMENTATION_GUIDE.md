# 동시성 제어 구현 가이드 (Implementation Guide)

> **목적**: E-Commerce 시스템의 주요 동시성 문제에 대한 실제 구현 코드와 Best Practice를 제공한다.

---

## 📌 구현 우선순위

1. ✅ **재고 차감** - Pessimistic Lock
2. ✅ **선착순 쿠폰** - Redis Distributed Lock
3. ✅ **결제 처리** - Idempotency Key + Pessimistic Lock
4. ✅ **잔액 업데이트** - Atomic Update
5. ✅ **주문 상태** - Optimistic Lock

---

## 1. 재고 차감 - Pessimistic Lock

### 📝 요구사항
- 동시에 여러 사용자가 마지막 재고를 구매 시도해도 정확히 1명만 성공
- 음수 재고 발생 절대 불가
- Over-selling 방지

### 🏗️ Entity 설계

```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Long price;

    @Column(nullable = false)
    private Integer stock;

    protected Product() {}

    public Product(String name, Long price, Integer stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /**
     * 재고 차감
     * @throws InsufficientStockException 재고 부족 시
     */
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new InsufficientStockException(
                String.format("재고 부족. 요청: %d, 현재: %d", quantity, this.stock)
            );
        }
        this.stock -= quantity;
    }

    /**
     * 재고 복구 (주문 취소 시)
     */
    public void restoreStock(int quantity) {
        this.stock += quantity;
    }

    // Getter
    public Integer getStock() {
        return stock;
    }
}
```

### 🗄️ Repository

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Pessimistic Write Lock으로 상품 조회
     * SELECT * FROM products WHERE id = ? FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
}
```

### 💼 UseCase (Application Layer)

```java
@Service
@RequiredArgsConstructor
public class StockUseCase {

    private final ProductRepository productRepository;

    /**
     * 재고 차감 (Pessimistic Lock 사용)
     *
     * @param productId 상품 ID
     * @param quantity 차감 수량
     * @return 차감 후 남은 재고
     */
    @Transactional
    public int decreaseStock(Long productId, int quantity) {
        // 1. Pessimistic Lock으로 상품 조회
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // 2. 재고 차감 (비즈니스 로직)
        product.decreaseStock(quantity);

        // 3. 자동 저장 (Dirty Checking)
        return product.getStock();
    }

    /**
     * 재고 복구 (주문 취소 시)
     */
    @Transactional
    public void restoreStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        product.restoreStock(quantity);
    }
}
```

### 🎯 Lock Timeout 설정

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({
    @QueryHint(name = "javax.persistence.lock.timeout", value = "3000") // 3초
})
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

### ⚠️ Deadlock 방지

```java
/**
 * 여러 상품 동시 구매 시 Deadlock 방지
 * - 항상 ID 오름차순으로 락 획득
 */
@Transactional
public void purchaseMultipleProducts(List<Long> productIds, Map<Long, Integer> quantities) {
    // Deadlock 방지: ID 정렬
    Collections.sort(productIds);

    for (Long productId : productIds) {
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        Integer quantity = quantities.get(productId);
        product.decreaseStock(quantity);
    }
}
```

### 📊 성능 최적화

#### 인덱스 추가
```sql
-- Primary Key는 자동으로 인덱스 생성됨
CREATE INDEX idx_product_stock ON products(stock) WHERE stock > 0;
```

#### 트랜잭션 최소화
```java
@Transactional
public void decreaseStock(Long productId, int quantity) {
    // ✅ 좋은 예: Lock 보유 시간 최소화
    Product product = productRepository.findByIdWithLock(productId)
        .orElseThrow();

    product.decreaseStock(quantity);
    // 트랜잭션 종료 (Lock 해제)
}

@Transactional
public void purchaseProductBad(Long productId, int quantity) {
    // ❌ 나쁜 예: 불필요한 작업을 트랜잭션 내에서 수행
    Product product = productRepository.findByIdWithLock(productId)
        .orElseThrow();

    product.decreaseStock(quantity);

    // 외부 API 호출 (5초 소요) - Lock 보유 시간 증가!
    externalService.notifyStockChange(productId);

    sendEmail(product); // 이메일 발송 (3초) - Lock 보유 시간 증가!
}
```

---

## 2. 선착순 쿠폰 발급 - Redis Distributed Lock

### 📝 요구사항
- 선착순 100명만 쿠폰 발급
- 정확히 100개만 발급 (101개 발급 절대 불가)
- 중복 발급 방지 (1인 1매)
- 초당 10,000+ TPS 처리

### 🏗️ Entity 설계

```java
@Entity
@Table(name = "user_coupons")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    protected UserCoupon() {}

    public static UserCoupon issue(Long userId, Long couponId) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.userId = userId;
        userCoupon.couponId = couponId;
        userCoupon.issuedAt = Instant.now();
        userCoupon.status = CouponStatus.AVAILABLE;
        return userCoupon;
    }
}

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    private Long id;

    private String name;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer issuedQuantity;

    public boolean isAvailable() {
        return issuedQuantity < totalQuantity;
    }
}
```

### 🗄️ Repository

```java
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}

public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
```

### 💼 UseCase (Redisson 사용)

```java
@Service
@RequiredArgsConstructor
public class CouponUseCase {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;

    /**
     * 선착순 쿠폰 발급
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 결과
     */
    public CouponIssueResult issueCoupon(Long couponId, Long userId) {
        String lockKey = "lock:coupon:" + couponId;
        String stockKey = "coupon:" + couponId + ":stock";

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Lock 획득 (100ms 대기, 3초 후 자동 해제)
            boolean acquired = lock.tryLock(100, 3000, TimeUnit.MILLISECONDS);

            if (!acquired) {
                return CouponIssueResult.failure("잠시 후 다시 시도해주세요");
            }

            // 중복 발급 체크
            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                return CouponIssueResult.failure("이미 발급받은 쿠폰입니다");
            }

            // Redis에서 재고 차감 (원자적 연산)
            Long remaining = redisTemplate.opsForValue().decrement(stockKey);

            if (remaining < 0) {
                // 재고 부족: 원복
                redisTemplate.opsForValue().increment(stockKey);
                return CouponIssueResult.failure("쿠폰이 모두 소진되었습니다");
            }

            // DB에 비동기 저장
            CompletableFuture.runAsync(() ->
                saveCouponToDB(couponId, userId)
            );

            return CouponIssueResult.success(remaining);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CouponIssueException("쿠폰 발급 중 오류 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * DB에 쿠폰 저장 (비동기)
     */
    @Async
    @Transactional
    protected void saveCouponToDB(Long couponId, Long userId) {
        UserCoupon userCoupon = UserCoupon.issue(userId, couponId);
        userCouponRepository.save(userCoupon);

        // 쿠폰 발급 수량 증가
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow();
        coupon.increaseIssued();
    }

    /**
     * Redis 재고 초기화
     */
    @PostConstruct
    public void initializeCouponStock() {
        List<Coupon> coupons = couponRepository.findAll();

        for (Coupon coupon : coupons) {
            String stockKey = "coupon:" + coupon.getId() + ":stock";
            int remaining = coupon.getTotalQuantity() - coupon.getIssuedQuantity();
            redisTemplate.opsForValue().set(stockKey, String.valueOf(remaining));
        }
    }
}

@Getter
public class CouponIssueResult {
    private final boolean success;
    private final String message;
    private final Long remainingQuantity;

    public static CouponIssueResult success(Long remaining) {
        return new CouponIssueResult(true, "발급 성공", remaining);
    }

    public static CouponIssueResult failure(String message) {
        return new CouponIssueResult(false, message, null);
    }
}
```

### 🔧 Redis 설정

```java
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://localhost:6379")
            .setConnectionPoolSize(50)
            .setConnectionMinimumIdleSize(10)
            .setRetryAttempts(3)
            .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
```

### ⚡ Lua Script를 활용한 고성능 구현

```java
public CouponIssueResult issueCouponFast(Long couponId, Long userId) {
    String luaScript = """
        local stock = redis.call('get', KEYS[1])
        if not stock or tonumber(stock) <= 0 then
            return -1
        end

        local issued_users = redis.call('sadd', KEYS[2], ARGV[1])
        if issued_users == 0 then
            return -2
        end

        redis.call('decr', KEYS[1])
        return tonumber(stock) - 1
    """;

    DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);

    Long result = redisTemplate.execute(
        script,
        List.of(
            "coupon:" + couponId + ":stock",
            "coupon:" + couponId + ":users"
        ),
        String.valueOf(userId)
    );

    if (result == -1) {
        return CouponIssueResult.failure("쿠폰 소진");
    } else if (result == -2) {
        return CouponIssueResult.failure("이미 발급받음");
    }

    // DB 비동기 저장
    saveCouponToDB(couponId, userId);

    return CouponIssueResult.success(result);
}
```

---

## 3. 결제 처리 - Idempotency Key

### 📝 요구사항
- 중복 결제 절대 불가
- 네트워크 재시도에도 안전
- 24시간 내 동일 요청 처리 방지

### 🏗️ Entity 설계

```java
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private Instant createdAt;

    protected Payment() {}

    public static Payment create(String idempotencyKey, Long orderId, Long userId, Integer amount) {
        Payment payment = new Payment();
        payment.idempotencyKey = idempotencyKey;
        payment.orderId = orderId;
        payment.userId = userId;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = Instant.now();
        return payment;
    }

    public void markAsSuccess() {
        this.status = PaymentStatus.SUCCESS;
    }

    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }
}
```

### 🗄️ Repository

```java
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}

public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}
```

### 💼 UseCase

```java
@Service
@RequiredArgsConstructor
public class PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * 결제 처리 (멱등성 보장)
     *
     * @param idempotencyKey 멱등성 키 (클라이언트 생성)
     * @param request 결제 요청
     * @return 결제 결과
     */
    @Transactional
    public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
        // 1차 방어: 멱등성 체크 (중복 요청 차단)
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Duplicate payment request detected: {}", idempotencyKey);
            return PaymentResult.from(existingPayment.get());
        }

        // 2차 방어: Pessimistic Lock (동시 결제 차단)
        User user = userRepository.findByIdWithLock(request.getUserId())
            .orElseThrow(() -> new UserNotFoundException(request.getUserId()));

        // 잔액 확인
        if (user.getBalance() < request.getAmount()) {
            throw new InsufficientBalanceException(
                String.format("잔액 부족. 현재: %d, 요청: %d", user.getBalance(), request.getAmount())
            );
        }

        // 잔액 차감
        user.deductBalance(request.getAmount());

        // 주문 상태 변경
        Order order = orderRepository.findById(request.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException("결제할 수 없는 주문 상태입니다: " + order.getStatus());
        }

        order.markAsPaid();

        // 결제 기록 저장 (Idempotency Key 포함)
        Payment payment = Payment.create(
            idempotencyKey,
            request.getOrderId(),
            request.getUserId(),
            request.getAmount()
        );

        try {
            // 외부 PG 호출 (타임아웃 3초)
            PGResponse pgResponse = pgService.charge(request);
            payment.markAsSuccess();

        } catch (Exception e) {
            payment.markAsFailed();
            throw new PaymentProcessingException("결제 처리 실패", e);
        }

        paymentRepository.save(payment);

        return PaymentResult.success(payment);
    }
}

@Getter
@AllArgsConstructor
public class PaymentRequest {
    private Long userId;
    private Long orderId;
    private Integer amount;
}

@Getter
public class PaymentResult {
    private final boolean success;
    private final String message;
    private final PaymentStatus status;

    public static PaymentResult from(Payment payment) {
        return new PaymentResult(
            payment.getStatus() == PaymentStatus.SUCCESS,
            payment.getStatus().name(),
            payment.getStatus()
        );
    }

    public static PaymentResult success(Payment payment) {
        return new PaymentResult(true, "결제 성공", PaymentStatus.SUCCESS);
    }
}
```

### 🌐 Controller (Idempotency Key 처리)

```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentUseCase paymentUseCase;

    @PostMapping
    public ApiResponse<PaymentResult> processPayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PaymentRequest request
    ) {
        // Idempotency Key 검증
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key 헤더가 필요합니다");
        }

        PaymentResult result = paymentUseCase.processPayment(idempotencyKey, request);
        return ApiResponse.success(result);
    }
}
```

### 🗄️ DB 스키마

```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(100) NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_idempotency (idempotency_key),
    INDEX idx_order_id (order_id),
    INDEX idx_user_id (user_id)
);
```

### 💡 전문가 의견: 외부 API 호출과 트랜잭션 분리

#### 제이 코치 (멘토링, 실무 경험)
> "외부 API 호출은 트랜잭션 밖으로 빼야 합니다. 레이턴시가 길어져서 커넥션 풀도 고갈되고, 메모리 버퍼풀 캐시가 증가하고, Undo Log가 쌓입니다."

#### 박트래픽 (성능 전문가, 15년차)
> "외부 API를 트랜잭션 안에서 호출하면 DB 커넥션을 5초, 10초씩 점유하게 됩니다. 100개의 커넥션 풀이 있어도 초당 20건밖에 처리하지 못합니다."

#### ❌ 나쁜 예: 트랜잭션 안에서 외부 API 호출

```java
@Transactional  // ❌ 문제!
public PaymentResult processPayment(PaymentRequest request) {
    // 1. 주문 조회 및 락 획득
    Order order = orderRepository.findByIdWithLock(request.getOrderId());

    // 2. 잔액 차감
    User user = userRepository.findByIdWithLock(request.getUserId());
    user.deductBalance(request.getAmount());

    // 3. 외부 PG API 호출 (5초 소요)
    // ⏰ 이 동안 DB 커넥션 점유!
    // ⏰ 이 동안 락 보유!
    // ⏰ 이 동안 다른 트랜잭션 대기!
    PGResponse pgResponse = pgService.charge(request);

    if (pgResponse.isSuccess()) {
        order.markAsPaid();
    } else {
        throw new PaymentFailedException();  // 롤백
    }

    return PaymentResult.success();
}

// 문제점:
// 1. 커넥션 풀 고갈 (초당 20건 주문 → 10개 커넥션이면 절반은 대기)
// 2. 락 보유 시간 증가 (5초 동안 다른 사람 대기)
// 3. 메모리 증가 (Undo Log, Buffer Pool)
```

#### ✅ 좋은 예: 트랜잭션 분리

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PGService pgService;

    // 1. 트랜잭션: 잔액 차감만 (빠르게 완료)
    @Transactional
    public Payment reservePayment(PaymentRequest request) {
        User user = userRepository.findByIdWithLock(request.getUserId());
        user.deductBalance(request.getAmount());

        Order order = orderRepository.findById(request.getOrderId())
            .orElseThrow();
        order.markAsPending();  // 결제 대기 상태

        Payment payment = Payment.create(request, PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    // 2. 트랜잭션 밖: 외부 API 호출
    public PaymentResult processPayment(PaymentRequest request) {
        // Step 1: 잔액 차감 (트랜잭션, 50ms)
        Payment payment = reservePayment(request);

        try {
            // Step 2: 외부 API 호출 (트랜잭션 밖, 5초)
            PGResponse pgResponse = pgService.charge(request);

            if (pgResponse.isSuccess()) {
                // Step 3: 트랜잭션: 상태 업데이트만 (50ms)
                updatePaymentSuccess(payment.getId(), pgResponse.getTransactionId());
                return PaymentResult.success();
            } else {
                // Step 4: 보상 트랜잭션: 잔액 복구 (50ms)
                compensatePayment(payment.getId());
                return PaymentResult.failure("PG 승인 실패");
            }
        } catch (Exception e) {
            // Step 5: 보상 트랜잭션: 잔액 복구
            compensatePayment(payment.getId());
            throw new PaymentProcessingException(e);
        }
    }

    @Transactional
    protected void updatePaymentSuccess(Long paymentId, String txId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.markAsSuccess(txId);

        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow();
        order.markAsPaid();
    }

    @Transactional
    protected void compensatePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.markAsFailed();

        User user = userRepository.findById(payment.getUserId()).orElseThrow();
        user.restoreBalance(payment.getAmount());  // 잔액 복구

        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow();
        order.markAsFailed();
    }
}
```

#### 김데이터 (DBA, 20년차)
> "보상 트랜잭션(Compensation Transaction) 패턴을 사용하면 외부 API 실패 시에도 데이터 일관성을 유지할 수 있습니다. SAGA 패턴의 기본 개념입니다."

#### 보상 트랜잭션이 필요한 이유

```
정상 흐름:
잔액 차감 (✅ 완료) → PG 승인 (✅ 성공) → 주문 완료 (✅ 성공)

실패 시나리오 1: PG 승인 실패
잔액 차감 (✅ 완료) → PG 승인 (❌ 실패)
→ 보상: 잔액 복구 필요!

실패 시나리오 2: 네트워크 타임아웃
잔액 차감 (✅ 완료) → PG 승인 (⏰ 타임아웃)
→ 보상: 잔액 복구 필요!

실패 시나리오 3: 주문 상태 업데이트 실패
잔액 차감 (✅ 완료) → PG 승인 (✅ 성공) → 주문 상태 (❌ DB 오류)
→ 보상: 잔액 복구 + PG 취소 API 호출 필요!
```

#### 이금융 (금융권, 12년차)
> "금융권에서는 외부 API 호출 전후로 상태를 기록합니다. PENDING → PROCESSING → SUCCESS/FAILED 같은 세밀한 상태 관리가 필요합니다."

#### 성능 비교

| 방식 | 커넥션 보유 시간 | 동시 처리 가능 (10개 커넥션) | 락 보유 시간 |
|------|----------------|------------------------|-----------|
| **트랜잭션 안** | 5초 (API 포함) | 초당 2건 | 5초 |
| **트랜잭션 밖** | 50ms (DB만) | 초당 200건 | 50ms |

#### 정스타트업 (CTO, 7년차)
> "처음에는 간단하게 트랜잭션 안에서 모두 처리했다가 트래픽이 늘면서 커넥션 풀 고갈 문제를 겪었습니다. 외부 API는 반드시 트랜잭션 밖에서 호출하세요."

---

## 4. 잔액 업데이트 - Atomic Update

### 📝 요구사항
- 충전과 차감이 동시에 발생해도 정확한 잔액 유지
- Lost Update 방지
- 음수 잔액 발생 불가

### 🏗️ Entity 설계

```java
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false)
    private Integer balance;

    public void chargeBalance(int amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("충전 금액은 0보다 커야 합니다");
        }
        this.balance += amount;
    }

    public void deductBalance(int amount) {
        if (this.balance < amount) {
            throw new InsufficientBalanceException(
                String.format("잔액 부족. 현재: %d, 요청: %d", this.balance, amount)
            );
        }
        this.balance -= amount;
    }
}
```

### 🗄️ Repository (Atomic Update)

```java
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 원자적 잔액 증가
     */
    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance + :amount WHERE u.id = :id")
    void increaseBalance(@Param("id") Long id, @Param("amount") int amount);

    /**
     * 원자적 잔액 차감 (잔액 부족 시 실패)
     */
    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance - :amount " +
           "WHERE u.id = :id AND u.balance >= :amount")
    int decreaseBalance(@Param("id") Long id, @Param("amount") int amount);
}
```

### 💼 UseCase

```java
@Service
@RequiredArgsConstructor
public class UserUseCase {

    private final UserRepository userRepository;

    /**
     * 잔액 충전 (Atomic Update)
     */
    @Transactional
    public int chargeBalance(Long userId, int amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("충전 금액은 0보다 커야 합니다");
        }

        // 원자적 업데이트
        userRepository.increaseBalance(userId, amount);

        // 현재 잔액 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        return user.getBalance();
    }

    /**
     * 잔액 차감 (Atomic Update)
     */
    @Transactional
    public int deductBalance(Long userId, int amount) {
        // 원자적 업데이트 (잔액 부족 시 0 반환)
        int updated = userRepository.decreaseBalance(userId, amount);

        if (updated == 0) {
            throw new InsufficientBalanceException("잔액이 부족합니다");
        }

        // 현재 잔액 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        return user.getBalance();
    }
}
```

### 🗄️ DB Constraint (음수 방지)

```sql
ALTER TABLE users
ADD CONSTRAINT chk_balance_positive
CHECK (balance >= 0);
```

---

## 5. 주문 상태 전이 - Optimistic Lock

### 📝 요구사항
- 올바른 상태 전이 순서 보장 (PENDING → PAID → SHIPPING → DELIVERED)
- 동시 상태 변경 방지

### 🏗️ Entity 설계

```java
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Version  // Optimistic Lock
    private Integer version;

    public void markAsPaid() {
        validateTransition(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
    }

    public void startShipping() {
        validateTransition(OrderStatus.SHIPPING);
        this.status = OrderStatus.SHIPPING;
    }

    private void validateTransition(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(
                String.format("상태 전이 불가: %s → %s", this.status, newStatus)
            );
        }
    }
}

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPING,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPING || target == CANCELLED;
            case SHIPPING -> target == DELIVERED;
            default -> false;
        };
    }
}
```

### 💼 UseCase

```java
@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final OrderRepository orderRepository;

    @Transactional
    public void markOrderAsPaid(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 상태 전이 검증 + Optimistic Lock
        order.markAsPaid();

        // Dirty Checking으로 자동 업데이트 (version 증가)
    }
}
```

---

## 6. 분산 Scheduler - ShedLock

### 📝 요구사항
- 여러 서버에서 동일한 스케줄러가 실행되어도 한 번만 실행
- 일일 매출 집계, 통계 계산 등 배치 작업에 사용
- 서버 장애 시에도 다른 서버가 이어서 실행

### 💡 전문가 의견: 분산 환경에서 스케줄러 관리

#### 제이 코치 (멘토링, 실무 경험)
> "여러 서버가 동시에 스케줄러를 실행하면 중복 집계가 발생하니까 ShedLock 같은 라이브러리로 한 서버만 실행되도록 보장해야 합니다."

#### 최아키텍트 (MSA, 10년차)
> "MSA 환경에서는 Auto-scaling으로 인스턴스가 동적으로 늘어나기 때문에 분산 락 없이는 스케줄러를 사용할 수 없습니다. ShedLock은 필수입니다."

### ❌ 문제 상황: 중복 실행

```java
// 3대의 서버가 모두 실행
@Scheduled(cron = "0 0 0 * * *")  // 매일 자정
public void aggregateDailySales() {
    // 일일 매출 집계
    List<Order> todayOrders = orderRepository.findToday();
    int totalSales = todayOrders.stream()
        .mapToInt(Order::getAmount)
        .sum();

    // DB에 저장
    salesRepository.save(new DailySales(LocalDate.now(), totalSales));
}

// 결과:
// Server 1: DailySales(2025-11-18, 1000만원) 저장
// Server 2: DailySales(2025-11-18, 1000만원) 저장  // 중복!
// Server 3: DailySales(2025-11-18, 1000만원) 저장  // 중복!
```

### ✅ 해결: ShedLock 사용

#### 1. 의존성 추가

```groovy
// build.gradle
dependencies {
    implementation 'net.javacrumbs.shedlock:shedlock-spring:5.9.0'
    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.9.0'
}
```

#### 2. DB 테이블 생성

```sql
-- MySQL
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    INDEX idx_lock_until (lock_until)
);

-- PostgreSQL
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_lock_until ON shedlock(lock_until);
```

#### 3. ShedLock 설정

```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()  // DB 시간 사용 (서버 시간 차이 방지)
            .build()
        );
    }
}
```

#### 4. 스케줄러에 적용

```java
@Component
@RequiredArgsConstructor
public class SalesAggregationScheduler {

    private final OrderRepository orderRepository;
    private final SalesRepository salesRepository;

    @Scheduled(cron = "0 0 0 * * *")  // 매일 자정
    @SchedulerLock(
        name = "dailySalesAggregation",
        lockAtMostFor = "9m",  // 최대 9분 동안 락 유지 (이후 자동 해제)
        lockAtLeastFor = "1m"  // 최소 1분 동안 락 유지 (너무 빨리 끝나도 1분 유지)
    )
    public void aggregateDailySales() {
        log.info("Starting daily sales aggregation");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 일일 매출 집계
        List<Order> orders = orderRepository.findByCreatedAtBetween(
            yesterday.atStartOfDay(),
            yesterday.plusDays(1).atStartOfDay()
        );

        int totalSales = orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .mapToInt(Order::getTotalAmount)
            .sum();

        // DB에 저장
        DailySales dailySales = new DailySales(yesterday, totalSales, orders.size());
        salesRepository.save(dailySales);

        log.info("Daily sales aggregation completed: date={}, totalSales={}, orderCount={}",
            yesterday, totalSales, orders.size());
    }
}

// 결과:
// 00:00:00 - Server 1이 락 획득, 집계 시작
// 00:00:00 - Server 2, 3은 락 획득 실패 → 종료 (로그: "not executing, already locked")
// 00:00:05 - Server 1 집계 완료
// 00:01:00 - 1분 후 락 자동 해제
```

### 동작 원리

#### 김데이터 (DBA, 20년차)
> "ShedLock은 DB의 `shedlock` 테이블에 락을 기록합니다. `name` 컬럼이 PRIMARY KEY라서 중복 INSERT가 불가능하고, 이를 이용해 분산 락을 구현합니다."

```sql
-- 00:00:00 Server 1 실행
INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
VALUES ('dailySalesAggregation', '2025-11-18 00:09:00', '2025-11-18 00:00:00', 'Server1-192.168.1.10')
ON DUPLICATE KEY UPDATE
    lock_until = IF(lock_until <= NOW(), VALUES(lock_until), lock_until),
    locked_at = IF(lock_until <= NOW(), VALUES(locked_at), locked_at),
    locked_by = IF(lock_until <= NOW(), VALUES(locked_by), locked_by);
-- 성공! (lock_until이 만료되었거나 없으면 획득)

-- 00:00:00 Server 2 실행
INSERT INTO shedlock ...;
-- 실패! (lock_until이 아직 유효함, 업데이트되지 않음)

-- 00:00:00 Server 3 실행
INSERT INTO shedlock ...;
-- 실패!
```

### lockAtMostFor vs lockAtLeastFor

#### 박트래픽 (성능 전문가, 15년차)
> "`lockAtMostFor`는 서버 장애 시 무한정 락이 걸리는 것을 방지하고, `lockAtLeastFor`는 너무 빨리 끝나서 중복 실행되는 것을 방지합니다."

**lockAtMostFor (최대 락 유지 시간):**
```
Server 1이 락 획득 후 장애 발생
→ 9분 후 자동 해제
→ Server 2가 락 획득하여 작업 재개
```

**lockAtLeastFor (최소 락 유지 시간):**
```
Server 1이 10초 만에 작업 완료
→ 그래도 1분 동안 락 유지
→ 다른 서버가 중복 실행하지 못하도록 방지
```

### 여러 스케줄러 관리

```java
@Component
@RequiredArgsConstructor
public class SchedulerTasks {

    // 일일 매출 집계
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "dailySalesAggregation", lockAtMostFor = "9m", lockAtLeastFor = "1m")
    public void aggregateDailySales() {
        // ...
    }

    // 인기 상품 갱신 (10분마다)
    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(name = "updatePopularProducts", lockAtMostFor = "9m", lockAtLeastFor = "1m")
    public void updatePopularProducts() {
        // ...
    }

    // 만료된 쿠폰 정리 (1시간마다)
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "cleanupExpiredCoupons", lockAtMostFor = "50m", lockAtLeastFor = "5m")
    public void cleanupExpiredCoupons() {
        // ...
    }
}
```

### 정스타트업 (CTO, 7년차)
> "처음에는 단일 서버였지만 트래픽이 늘어나면서 3대로 늘렸는데, 스케줄러가 3배로 실행되는 걸 깨닫고 급하게 ShedLock을 도입했습니다. 처음부터 적용하는 게 좋습니다."

### 모니터링

```java
@Component
@RequiredArgsConstructor
public class ShedLockMetrics {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 60000)  // 1분마다
    public void recordLockMetrics() {
        // 현재 락 상태 조회
        List<Map<String, Object>> locks = jdbcTemplate.queryForList(
            "SELECT name, lock_until, locked_by FROM shedlock WHERE lock_until > NOW()"
        );

        meterRegistry.gauge("shedlock.active_locks", locks.size());

        for (Map<String, Object> lock : locks) {
            log.info("Active lock: name={}, until={}, by={}",
                lock.get("name"),
                lock.get("lock_until"),
                lock.get("locked_by")
            );
        }
    }
}
```

### Entity 설계 (DailySales)

```java
@Entity
@Table(name = "daily_sales")
public class DailySales {

    @Id
    private LocalDate salesDate;

    @Column(nullable = false)
    private Integer totalAmount;

    @Column(nullable = false)
    private Integer orderCount;

    @Column(nullable = false)
    private Instant aggregatedAt;

    protected DailySales() {}

    public DailySales(LocalDate salesDate, Integer totalAmount, Integer orderCount) {
        this.salesDate = salesDate;
        this.totalAmount = totalAmount;
        this.orderCount = orderCount;
        this.aggregatedAt = Instant.now();
    }
}
```

---

## 📚 다음 문서

- **테스트 전략**: [TEST_STRATEGY.md](./TEST_STRATEGY.md)
- **성능 최적화**: [PERFORMANCE_OPTIMIZATION.md](./PERFORMANCE_OPTIMIZATION.md)

---

**작성일**: 2025-11-18
**버전**: 1.0
