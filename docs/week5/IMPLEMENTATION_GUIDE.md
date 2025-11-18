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

## 📚 다음 문서

- **테스트 전략**: [TEST_STRATEGY.md](./TEST_STRATEGY.md)
- **성능 최적화**: [PERFORMANCE_OPTIMIZATION.md](./PERFORMANCE_OPTIMIZATION.md)

---

**작성일**: 2025-11-18
**버전**: 1.0
