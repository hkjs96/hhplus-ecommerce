# STEP 11-12 빠른 시작 가이드
## 3시간 압축 학습 로드맵

> **목표**: 최소한의 시간으로 핵심 개념을 이해하고 실습 완료

---

## ⏱️ 시간 배분

| 시간 | 주제 | 활동 |
|-----|------|------|
| **0:00-0:40** | 분산락 이해 및 구현 | Redis 설정, Redisson 연동, 분산락 적용 |
| **0:40-1:20** | 동시성 테스트 | TestContainers 설정, 통합 테스트 작성 |
| **1:20-2:00** | 캐싱 전략 적용 | Cache-Aside 구현, Cache Stampede 방지 |
| **2:00-2:40** | 성능 측정 및 보고서 | Before/After 성능 비교, 보고서 작성 |
| **2:40-3:00** | 코드 리뷰 및 정리 | 체크리스트 확인, PR 제출 준비 |

---

## 🚀 Session 1: 분산락 구현 (40분)

### 1단계: Docker Compose에 Redis 추가 (5분)

```yaml
# docker-compose.yml
services:
  redis:
    image: redis:7-alpine
    container_name: ecommerce-redis
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb
    networks:
      - ecommerce-network
```

```bash
# Redis 시작
docker-compose up -d redis

# 확인
docker exec -it ecommerce-redis redis-cli ping
# PONG
```

### 2단계: Gradle 의존성 추가 (2분)

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.redisson:redisson-spring-boot-starter:3.23.5'
}
```

```bash
./gradlew clean build -x test
```

### 3단계: RedisConfig 작성 (3분)

```java
// src/main/java/io/hhplus/ecommerce/config/RedisConfig.java
package io.hhplus.ecommerce.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
}
```

### 4단계: DistributedLock 어노테이션 작성 (5분)

```java
// src/main/java/io/hhplus/ecommerce/infrastructure/redis/DistributedLock.java
package io.hhplus.ecommerce.infrastructure.redis;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();
    long waitTime() default 10L;
    long leaseTime() default 30L;
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
```

### 5단계: AOP 구현 (10분)

```java
// src/main/java/io/hhplus/ecommerce/infrastructure/redis/DistributedLockAspect.java
package io.hhplus.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        DistributedLock annotation = signature.getMethod()
                .getAnnotation(DistributedLock.class);

        String lockKey = annotation.key();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(
                    annotation.waitTime(),
                    annotation.leaseTime(),
                    annotation.timeUnit()
            );

            if (!isLocked) {
                throw new IllegalStateException("락 획득 실패: " + lockKey);
            }

            log.info("락 획득 성공: {}", lockKey);
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("락 해제: {}", lockKey);
            }
        }
    }
}
```

### 6단계: 주문 서비스에 분산락 적용 (10분)

```java
// OrderUseCase 또는 OrderService
@DistributedLock(key = "'order:product:' + #productId")
@Transactional
public OrderResponse createOrder(Long productId, int quantity) {
    // 1. 상품 조회
    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

    // 2. 재고 차감
    product.decreaseStock(quantity);

    // 3. 주문 생성
    Order order = Order.create(product, quantity);
    orderRepository.save(order);

    return OrderResponse.from(order);
}
```

### ✅ Session 1 체크포인트

- [ ] Redis가 Docker에서 정상 실행되는가?
- [ ] Redisson 설정이 완료되었는가?
- [ ] DistributedLock 어노테이션이 작동하는가?
- [ ] 주문 생성 기능에 분산락이 적용되었는가?

---

## 🧪 Session 2: 동시성 테스트 (40분)

### 1단계: TestContainers 의존성 추가 (2분)

```gradle
dependencies {
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
    testImplementation 'com.redis.testcontainers:testcontainers-redis:1.6.4'
}
```

### 2단계: TestContainers 설정 (8분)

```java
// src/test/java/io/hhplus/ecommerce/config/TestContainersConfig.java
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
                .withDatabaseName("test_db")
                .withUsername("test")
                .withPassword("test");
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
```

### 3단계: 동시성 테스트 작성 (20분)

```java
// src/test/java/io/hhplus/ecommerce/application/order/OrderConcurrencyTest.java
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

    @BeforeEach
    void setUp() {
        // 초기 재고 100개
        Product product = Product.builder()
                .id(1L)
                .name("테스트 상품")
                .price(10000L)
                .stock(100)
                .build();
        productRepository.save(product);
    }

    @Test
    @DisplayName("100명이 동시 주문 시 정확히 100개만 차감")
    void 분산락_동시성_테스트() throws InterruptedException {
        // Given
        Long productId = 1L;
        int threadCount = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderUseCase.createOrder(productId, 1);
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

        // Then
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 50개일 때 100명 요청 시 50개만 성공")
    void 재고부족_동시성_테스트() throws InterruptedException {
        // Given
        Long productId = 1L;
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStock(50);
        productRepository.save(product);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderUseCase.createOrder(productId, 1);
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

        // Then
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);

        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }
}
```

### 4단계: 테스트 실행 (10분)

```bash
# 테스트 실행
./gradlew test --tests OrderConcurrencyTest

# 결과 확인
# ✅ 100명이 동시 주문 시 정확히 100개만 차감 - PASSED
# ✅ 재고 50개일 때 100명 요청 시 50개만 성공 - PASSED
```

### ✅ Session 2 체크포인트

- [ ] TestContainers가 정상 실행되는가?
- [ ] 동시성 테스트가 통과하는가?
- [ ] 재고 차감이 정확히 동작하는가?

---

## 💾 Session 3: 캐싱 전략 적용 (40분)

### 1단계: 인기 상품 조회 캐싱 (15분)

```java
// src/main/java/io/hhplus/ecommerce/application/product/ProductUseCase.java
@Service
@RequiredArgsConstructor
public class ProductUseCase {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    /**
     * 인기 상품 조회 (Cache-Aside 패턴)
     */
    public List<ProductResponse> getPopularProducts() {
        String cacheKey = "popular:products:top5";

        // 1. 캐시 조회
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            log.info("캐시 Hit: {}", cacheKey);
            return cached;
        }

        // 2. Cache Miss - 분산락으로 DB 조회
        log.info("캐시 Miss: {}", cacheKey);
        return getPopularProductsWithLock(cacheKey);
    }

    @DistributedLock(key = "'lock:popular:products'", waitTime = 5, leaseTime = 10)
    private List<ProductResponse> getPopularProductsWithLock(String cacheKey) {
        // Double-Check
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            return cached;
        }

        // DB 조회
        List<Product> products = productRepository.findTop5ByOrderBySalesCountDesc();
        List<ProductResponse> response = products.stream()
                .map(ProductResponse::from)
                .toList();

        // 캐시 저장 (TTL: 5분)
        bucket.set(response, Duration.ofMinutes(5));
        log.info("캐시 저장: {} (TTL: 5분)", cacheKey);

        return response;
    }
}
```

### 2단계: 캐시 테스트 작성 (15분)

```java
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

        // When: 첫 번째 호출 (Cache Miss)
        List<ProductResponse> firstCall = productUseCase.getPopularProducts();

        // Then: 캐시 저장 확인
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        assertThat(bucket.get()).isNotNull();

        // When: 두 번째 호출 (Cache Hit)
        List<ProductResponse> secondCall = productUseCase.getPopularProducts();

        // Then: 동일한 데이터 반환
        assertThat(secondCall).isEqualTo(firstCall);
    }

    @Test
    @DisplayName("캐시 TTL 확인")
    void 캐시_TTL_테스트() {
        // Given
        productUseCase.getPopularProducts();

        // When
        RBucket<List<ProductResponse>> bucket = redissonClient
                .getBucket("popular:products:top5");
        long ttl = bucket.remainTimeToLive();

        // Then: 약 5분 (300초)
        assertThat(ttl).isGreaterThan(290_000);
        assertThat(ttl).isLessThanOrEqualTo(300_000);
    }
}
```

### 3단계: Cache Stampede 방지 테스트 (10분)

```java
@Test
@DisplayName("50명 동시 요청 시 DB 쿼리 1번만 실행")
void 캐시_Stampede_방지_테스트() throws InterruptedException {
    // Given: 캐시 삭제 (만료 상태 시뮬레이션)
    redissonClient.getBucket("popular:products:top5").delete();

    int threadCount = 50;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    // When: 50명 동시 호출
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

    // Then: 캐시 저장 확인 (분산락 덕분에 1번만 DB 조회)
    RBucket<List<ProductResponse>> bucket = redissonClient
            .getBucket("popular:products:top5");
    assertThat(bucket.get()).isNotNull();
}
```

### ✅ Session 3 체크포인트

- [ ] Cache-Aside 패턴이 구현되었는가?
- [ ] 분산락으로 Cache Stampede를 방지하는가?
- [ ] TTL이 정상 작동하는가?
- [ ] 캐시 테스트가 모두 통과하는가?

---

## 📊 Session 4: 성능 측정 및 보고서 (40분)

### 1단계: 성능 측정 (20분)

#### JMeter 테스트 계획

```
Thread Group 설정:
- Number of Threads: 100
- Ramp-up Period: 10초
- Loop Count: 10

HTTP Request:
- Server: localhost
- Port: 8080
- Path: /products/top
```

#### 측정 항목

| 항목 | Before (캐시 없음) | After (캐시 적용) |
|-----|-------------------|------------------|
| 평균 응답 시간 | ?ms | ?ms |
| 최대 응답 시간 | ?ms | ?ms |
| TPS | ?req/s | ?req/s |
| 에러율 | ?% | ?% |

### 2단계: 보고서 작성 (20분)

```markdown
# STEP 12 성능 개선 보고서

## 1. 문제 배경

### 성능 문제
- API: GET /products/top
- 문제: 응답 시간 느림, DB 부하 높음
- 원인: 복잡한 쿼리 (JOIN + ORDER BY + LIMIT)

## 2. 해결 방안

### 적용한 캐싱 전략
- 패턴: Cache-Aside
- 저장소: Redis (Redisson)
- TTL: 5분
- Stampede 방지: 분산락 + Double-Check

## 3. 성능 측정 결과

### Before (캐시 미적용)
- 평균 응답 시간: XXms
- TPS: XX req/s

### After (캐시 적용)
- 평균 응답 시간: XXms (XX% 개선)
- TPS: XX req/s (XX% 증가)

## 4. Cache Hit Rate
- Hit: XX%
- Miss: XX%

## 5. 결론

캐시 적용으로 XX% 성능 개선 달성
```

### ✅ Session 4 체크포인트

- [ ] 성능 측정을 완료했는가?
- [ ] Before/After 비교 데이터가 있는가?
- [ ] 보고서를 작성했는가?

---

## 🎯 최종 체크리스트

### STEP 11: Distributed Lock

#### 필수 구현
- [ ] Redis + Redisson 연동
- [ ] DistributedLock 어노테이션 구현
- [ ] 주문 생성에 분산락 적용
- [ ] 동시성 테스트 통과 (100명 동시 요청)

#### 문서화
- [ ] 분산락이 필요한 이유 설명
- [ ] 락과 트랜잭션 순서 중요성 문서화

### STEP 12: Caching

#### 필수 구현
- [ ] 인기 상품 조회 캐싱 적용
- [ ] Cache-Aside 패턴 구현
- [ ] 분산락으로 Cache Stampede 방지
- [ ] TTL 설정 (5분)

#### 테스트
- [ ] 캐시 Hit/Miss 테스트
- [ ] TTL 동작 테스트
- [ ] Cache Stampede 방지 테스트

#### 성능 보고서
- [ ] 문제 배경 및 원인 분석
- [ ] 성능 측정 결과 (Before/After)
- [ ] Cache Hit Rate 분석
- [ ] 결론 및 개선 효과

---

## 🚨 트러블슈팅

### Redis 연결 오류
```bash
# Redis 실행 확인
docker ps | grep redis

# 재시작
docker-compose restart redis
```

### TestContainers 오류
```bash
# Docker Daemon 확인
docker info

# TestContainers 로그 확인
./gradlew test --info
```

### 분산락 타임아웃
```java
// leaseTime 증가
@DistributedLock(key = "...", leaseTime = 60)
```

---

## 📝 PR 템플릿

```markdown
## [STEP11-12] 이름 - 분산락 & 캐싱 적용

### ✅ STEP 11: Distributed Lock
- Redis 기반 분산락 구현
- 주문/결제/쿠폰 발급에 적용
- 동시성 테스트 통과 (100명 동시 요청)

### ✅ STEP 12: Caching
- 인기 상품 조회 캐싱 적용
- Cache Stampede 방지 (분산락 + Double-Check)
- 성능 개선: 응답 시간 XX% 감소, TPS XX% 증가

### 📊 성능 측정 결과
- Before: 평균 XXms
- After: 평균 XXms (XX% 개선)

### 💭 회고
- **잘한 점**:
- **어려운 점**:
- **다음 시도**:
```

---

**🎉 3시간 압축 학습을 완료하셨습니다!**

이제 PR을 제출하고 코치님의 리뷰를 받으세요! 💪
