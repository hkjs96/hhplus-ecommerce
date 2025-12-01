# STEP 11-12 코드 예제 모음
## 바로 복사해서 사용할 수 있는 실전 코드

---

## 📁 파일 구조

```
src/main/java/io/hhplus/ecommerce/
├── config/
│   └── RedisConfig.java                        # Redis 설정
├── infrastructure/
│   └── redis/
│       ├── DistributedLock.java                # 분산락 어노테이션
│       └── DistributedLockAspect.java          # 분산락 AOP
├── application/
│   ├── order/
│   │   └── OrderUseCase.java                   # 주문 (분산락 적용)
│   ├── payment/
│   │   └── PaymentUseCase.java                 # 결제 (분산락 적용)
│   ├── coupon/
│   │   └── CouponUseCase.java                  # 쿠폰 (분산락 적용)
│   └── product/
│       └── ProductUseCase.java                 # 인기 상품 (캐시 적용)
└── domain/
    └── product/
        └── ProductRepository.java

src/test/java/io/hhplus/ecommerce/
├── config/
│   └── TestContainersConfig.java               # TestContainers 설정
└── application/
    ├── order/
    │   └── OrderConcurrencyTest.java            # 주문 동시성 테스트
    └── product/
        └── ProductCacheTest.java                # 캐시 테스트
```

---

## 🔧 1. Redis 설정

### RedisConfig.java

```java
package io.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // Jackson Codec 설정 (JSON 직렬화)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        config.setCodec(new JsonJacksonCodec(objectMapper));

        // Redis 서버 설정
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionPoolSize(50)          // 커넥션 풀 크기
                .setConnectionMinimumIdleSize(10)   // 최소 유휴 커넥션
                .setRetryAttempts(3)                // 재시도 횟수
                .setRetryInterval(1500)             // 재시도 간격 (ms)
                .setTimeout(3000)                   // 응답 타임아웃 (ms)
                .setPingConnectionInterval(30000);  // Ping 간격 (30초)

        return Redisson.create(config);
    }
}
```

### application.yml

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 10
        max-idle: 10
        min-idle: 2
```

---

## 🔒 2. 분산락 구현

### DistributedLock.java (어노테이션)

```java
package io.hhplus.ecommerce.infrastructure.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 어노테이션
 *
 * 사용 예시:
 * @DistributedLock(key = "'order:product:' + #productId", waitTime = 10, leaseTime = 30)
 * public void createOrder(Long productId, int quantity) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락의 이름 (Redis Key)
     * SpEL 표현식 사용 가능
     *
     * 예시:
     * - "'lock:user:' + #userId"
     * - "'lock:product:' + #request.productId"
     */
    String key();

    /**
     * 락 획득을 위한 대기 시간 (기본 10초)
     * 이 시간 동안 락을 획득하지 못하면 예외 발생
     */
    long waitTime() default 10L;

    /**
     * 락 임대 시간 (기본 30초)
     * 이 시간이 지나면 자동으로 락 해제
     * 데드락 방지용
     */
    long leaseTime() default 30L;

    /**
     * 시간 단위 (기본 초)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
```

### DistributedLockAspect.java (AOP)

```java
package io.hhplus.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(io.hhplus.ecommerce.infrastructure.redis.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // SpEL 표현식 파싱
        String lockKey = parseLockKey(distributedLock.key(), signature, joinPoint.getArgs());

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도
            boolean isLocked = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!isLocked) {
                log.error("락 획득 실패: key={}, waitTime={}{}",
                        lockKey,
                        distributedLock.waitTime(),
                        distributedLock.timeUnit()
                );
                throw new IllegalStateException("락 획득 실패: " + lockKey);
            }

            log.info("락 획득 성공: key={}, leaseTime={}{}",
                    lockKey,
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            // 비즈니스 로직 실행
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트 발생: " + lockKey, e);
        } finally {
            // 락 해제 (반드시 현재 스레드가 보유한 경우만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("락 해제: key={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식 파싱
     *
     * 예시:
     * - "'lock:user:' + #userId" → "lock:user:123"
     * - "'lock:product:' + #request.productId" → "lock:product:456"
     */
    private String parseLockKey(String keyExpression, MethodSignature signature, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 SpEL Context에 등록
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
```

---

## 🛒 3. 주문 생성에 분산락 적용

### OrderUseCase.java

```java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.OrderResponse;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * 주문 생성 (분산락 적용)
     *
     * 동작 순서:
     * 1. 분산락 획득 (key: "order:product:{productId}")
     * 2. 트랜잭션 시작
     * 3. 상품 조회
     * 4. 재고 차감 (동시성 제어됨)
     * 5. 주문 생성
     * 6. 트랜잭션 커밋
     * 7. 분산락 해제
     */
    @DistributedLock(
            key = "'order:product:' + #request.productId",
            waitTime = 10,
            leaseTime = 30
    )
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("주문 생성 시작: productId={}, quantity={}",
                request.getProductId(), request.getQuantity());

        // 1. 상품 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "상품을 찾을 수 없습니다: " + request.getProductId()
                ));

        // 2. 재고 차감 (비즈니스 로직은 Entity에서)
        product.decreaseStock(request.getQuantity());

        // 3. 주문 생성
        Order order = Order.create(
                request.getUserId(),
                product,
                request.getQuantity()
        );

        orderRepository.save(order);

        log.info("주문 생성 완료: orderId={}", order.getId());

        return OrderResponse.from(order);
    }

    /**
     * 여러 상품 동시 주문 (상품 ID 오름차순으로 락 획득)
     *
     * 주의: 데드락 방지를 위해 항상 동일한 순서로 락 획득
     */
    @Transactional
    public OrderResponse createOrderWithMultipleProducts(CreateOrderRequest request) {
        // 상품 ID 정렬 (데드락 방지)
        List<Long> sortedProductIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .sorted()
                .toList();

        // 순서대로 락 획득 및 처리
        for (Long productId : sortedProductIds) {
            acquireLockAndDecreaseStock(productId, request);
        }

        // 주문 생성
        Order order = createOrderInternal(request);
        return OrderResponse.from(order);
    }

    @DistributedLock(key = "'order:product:' + #productId")
    private void acquireLockAndDecreaseStock(Long productId, CreateOrderRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 없음: " + productId));

        int quantity = request.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .mapToInt(OrderItemRequest::getQuantity)
                .sum();

        product.decreaseStock(quantity);
    }
}
```

---

## 💳 4. 결제 처리에 분산락 적용

### PaymentUseCase.java

```java
package io.hhplus.ecommerce.application.payment;

import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUseCase {

    private final UserRepository userRepository;

    /**
     * 결제 처리 (분산락 적용)
     *
     * 중요: 동일한 사용자에 대한 충전/결제가 동시에 발생하면 안 됨
     * 락 키: "payment:user:{userId}"
     */
    @DistributedLock(
            key = "'payment:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    @Transactional
    public PaymentResponse processPayment(Long userId, Long orderId, BigDecimal amount) {
        log.info("결제 처리 시작: userId={}, amount={}", userId, amount);

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        // 2. 잔액 차감 (비즈니스 로직은 Entity에서)
        user.deductBalance(amount);

        log.info("결제 완료: userId={}, 잔액={}", userId, user.getBalance());

        return PaymentResponse.success(orderId, amount, user.getBalance());
    }

    /**
     * 잔액 충전 (분산락 적용)
     *
     * 중요: 결제와 동일한 락 키 사용
     */
    @DistributedLock(
            key = "'payment:user:' + #userId",
            waitTime = 10,
            leaseTime = 30
    )
    @Transactional
    public void chargeBalance(Long userId, BigDecimal amount) {
        log.info("잔액 충전 시작: userId={}, amount={}", userId, amount);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        user.chargeBalance(amount);

        log.info("충전 완료: userId={}, 잔액={}", userId, user.getBalance());
    }
}
```

---

## 🎟️ 5. 쿠폰 발급에 분산락 적용

### CouponUseCase.java

```java
package io.hhplus.ecommerce.application.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급 (분산락 적용)
     *
     * 선착순 쿠폰의 경우 동시성 제어 필수!
     */
    @DistributedLock(
            key = "'coupon:issue:' + #couponId",
            waitTime = 5,
            leaseTime = 10
    )
    @Transactional
    public UserCouponResponse issueCoupon(Long userId, Long couponId) {
        log.info("쿠폰 발급 시작: userId={}, couponId={}", userId, couponId);

        // 1. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 없음"));

        // 2. 중복 발급 체크
        boolean alreadyIssued = userCouponRepository
                .existsByUserIdAndCouponId(userId, couponId);

        if (alreadyIssued) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다");
        }

        // 3. 쿠폰 발급 (수량 차감)
        coupon.issue();  // 비즈니스 로직은 Entity에서

        // 4. 사용자 쿠폰 생성
        UserCoupon userCoupon = UserCoupon.create(userId, coupon);
        userCouponRepository.save(userCoupon);

        log.info("쿠폰 발급 완료: userCouponId={}, 남은 수량={}",
                userCoupon.getId(), coupon.getRemainingQuantity());

        return UserCouponResponse.from(userCoupon);
    }
}
```

---

## 💾 6. 인기 상품 조회 캐싱

### ProductUseCase.java

```java
package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductUseCase {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    private static final String CACHE_KEY = "popular:products:top5";
    private static final String LOCK_KEY = "lock:popular:products";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 인기 상품 조회 (Cache-Aside 패턴 + 분산락)
     *
     * 동작 흐름:
     * 1. 캐시 조회 (Cache Hit 시 즉시 반환)
     * 2. Cache Miss 시 분산락 획득
     * 3. Double-Check (락 대기 중 다른 스레드가 캐싱했을 수 있음)
     * 4. DB 조회
     * 5. 캐시 저장 (TTL: 5분)
     * 6. 분산락 해제
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getPopularProducts() {
        // 1. 캐시 조회
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(CACHE_KEY);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            log.info("캐시 Hit: {}", CACHE_KEY);
            return cached;
        }

        log.info("캐시 Miss: {} - 분산락 획득 시도", CACHE_KEY);

        // 2. Cache Miss - 분산락으로 DB 조회 중복 방지
        return getPopularProductsWithLock();
    }

    /**
     * 분산락으로 DB 조회 (Cache Stampede 방지)
     */
    @DistributedLock(key = "'" + LOCK_KEY + "'", waitTime = 5, leaseTime = 10)
    private List<ProductResponse> getPopularProductsWithLock() {
        // Double-Check: 락 대기 중 다른 스레드가 캐싱했을 수 있음
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(CACHE_KEY);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            log.info("Double-Check 캐시 Hit: {}", CACHE_KEY);
            return cached;
        }

        // DB 조회
        log.info("DB 조회 시작: 인기 상품 Top 5");
        List<Product> products = productRepository.findTop5ByOrderBySalesCountDesc();

        List<ProductResponse> response = products.stream()
                .map(ProductResponse::from)
                .toList();

        // 캐시 저장 (TTL: 5분)
        bucket.set(response, CACHE_TTL);
        log.info("캐시 저장 완료: {} (TTL: {})", CACHE_KEY, CACHE_TTL);

        return response;
    }

    /**
     * 인기 상품 캐시 갱신 (Scheduled)
     *
     * 10분마다 실행하여 TTL 만료 전에 미리 갱신
     * → Cache Miss 최소화
     */
    @Scheduled(cron = "0 */10 * * * *")  // 매 10분마다
    public void refreshPopularProductsCache() {
        log.info("인기 상품 캐시 갱신 시작 (Scheduled)");

        try {
            List<Product> products = productRepository.findTop5ByOrderBySalesCountDesc();
            List<ProductResponse> response = products.stream()
                    .map(ProductResponse::from)
                    .toList();

            RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(CACHE_KEY);
            bucket.set(response, CACHE_TTL);

            log.info("인기 상품 캐시 갱신 완료: {} (TTL: {})", CACHE_KEY, CACHE_TTL);

        } catch (Exception e) {
            log.error("인기 상품 캐시 갱신 실패", e);
        }
    }

    /**
     * 캐시 즉시 삭제 (상품 정보 변경 시)
     */
    public void evictPopularProductsCache() {
        boolean deleted = redissonClient.getBucket(CACHE_KEY).delete();

        if (deleted) {
            log.info("인기 상품 캐시 삭제 완료: {}", CACHE_KEY);
        }
    }
}
```

---

## 🧪 7. 통합 테스트

### TestContainersConfig.java

```java
package io.hhplus.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("test_ecommerce")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci"
                );
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", "256mb");
    }
}
```

### OrderConcurrencyTest.java

```java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainersConfig.class)
class OrderConcurrencyTest {

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductRepository productRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        testProduct = Product.builder()
                .name("테스트 상품")
                .price(10000L)
                .stock(100)
                .build();

        productRepository.save(testProduct);
    }

    @Test
    @DisplayName("100명이 동시 주문 시 정확히 100개만 차감")
    void 분산락_동시성_테스트_정확한_재고차감() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 100명이 동시에 주문 (각 1개씩)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .productId(testProduct.getId())
                            .quantity(1)
                            .build();

                    orderUseCase.createOrder(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 100개만 성공, 재고 0개
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 50개일 때 100명 요청 시 정확히 50개만 성공")
    void 분산락_동시성_테스트_재고부족() throws InterruptedException {
        // Given: 재고 50개로 설정
        testProduct.setStock(50);
        productRepository.save(testProduct);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    CreateOrderRequest request = CreateOrderRequest.builder()
                            .productId(testProduct.getId())
                            .quantity(1)
                            .build();

                    orderUseCase.createOrder(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 50개만 성공, 50개 실패
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);

        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }
}
```

### ProductCacheTest.java

```java
package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainersConfig.class)
class ProductCacheTest {

    @Autowired
    private ProductUseCase productUseCase;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @DisplayName("캐시 Hit/Miss 동작 확인")
    void 캐시_동작_테스트() {
        // Given
        String cacheKey = "popular:products:top5";

        // When: 첫 번째 호출 (Cache Miss, DB 조회)
        List<ProductResponse> firstCall = productUseCase.getPopularProducts();

        // Then: 캐시에 저장되었는지 확인
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        assertThat(cached).isNotNull();
        assertThat(cached).hasSize(firstCall.size());

        // When: 두 번째 호출 (Cache Hit)
        List<ProductResponse> secondCall = productUseCase.getPopularProducts();

        // Then: 동일한 데이터 반환
        assertThat(secondCall).isEqualTo(firstCall);
    }

    @Test
    @DisplayName("캐시 TTL 확인 (약 5분)")
    void 캐시_TTL_테스트() {
        // Given
        productUseCase.getPopularProducts();

        // When
        RBucket<List<ProductResponse>> bucket = redissonClient
                .getBucket("popular:products:top5");
        long ttl = bucket.remainTimeToLive();  // 밀리초 단위

        // Then: 약 5분 (300초 = 300,000ms)
        assertThat(ttl).isGreaterThan(290_000);  // 최소 290초
        assertThat(ttl).isLessThanOrEqualTo(300_000);  // 최대 300초
    }

    @Test
    @DisplayName("50명 동시 요청 시 Cache Stampede 방지 확인")
    void 캐시_Stampede_방지_테스트() throws InterruptedException {
        // Given: 캐시 삭제 (만료 상태 시뮬레이션)
        String cacheKey = "popular:products:top5";
        redissonClient.getBucket(cacheKey).delete();

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When: 50명이 동시에 호출
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productUseCase.getPopularProducts();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 분산락 덕분에 캐시가 정상적으로 저장됨
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        assertThat(bucket.get()).isNotNull();

        // 실제로는 DB 쿼리가 1번만 실행되었는지 로그나 메트릭으로 확인
        // (이 테스트에서는 최종 결과만 검증)
    }

    @Test
    @DisplayName("캐시 삭제 후 재조회 시 DB에서 가져옴")
    void 캐시_삭제_테스트() {
        // Given: 캐시 저장
        List<ProductResponse> firstCall = productUseCase.getPopularProducts();

        // When: 캐시 삭제
        productUseCase.evictPopularProductsCache();

        // Then: 캐시에서 사라짐
        RBucket<List<ProductResponse>> bucket = redissonClient
                .getBucket("popular:products:top5");
        assertThat(bucket.get()).isNull();

        // When: 재조회 (Cache Miss, DB 조회)
        List<ProductResponse> secondCall = productUseCase.getPopularProducts();

        // Then: 새로 캐싱됨
        assertThat(bucket.get()).isNotNull();
        assertThat(secondCall).hasSize(firstCall.size());
    }
}
```

---

## 📝 정리

### 핵심 포인트

1. **분산락 적용 시 주의사항**
   - SpEL 표현식으로 동적 락 키 생성
   - waitTime과 leaseTime 적절히 설정
   - 반드시 finally 블록에서 락 해제

2. **캐시 적용 시 주의사항**
   - Cache-Aside 패턴 사용
   - Double-Check로 중복 DB 조회 방지
   - 분산락으로 Cache Stampede 방지
   - TTL 적절히 설정 (데이터 특성에 따라)

3. **테스트 작성 시 주의사항**
   - TestContainers로 실제 환경과 유사하게 구성
   - CountDownLatch로 동시성 정확히 제어
   - AtomicInteger로 스레드 안전하게 카운트

---

**🎉 이 코드들을 복사해서 프로젝트에 바로 적용하세요!**
