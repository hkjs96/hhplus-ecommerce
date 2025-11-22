# STEP 11-12 학습 가이드
## Distributed Lock & Caching Strategy

> **학습 기간**: 4일 (10시간) / 압축 학습: 3시간
> **목표**: Redis 기반 분산락과 캐싱 전략을 이해하고 실제 프로젝트에 적용

---

## 📚 목차

1. [학습 목표](#학습-목표)
2. [핵심 개념](#핵심-개념)
3. [Day 1: Redis 기초 & 동시성 복습](#day-1-redis-기초--동시성-복습)
4. [Day 2: Distributed Lock 구현](#day-2-distributed-lock-구현)
5. [Day 3: Caching Strategy 적용](#day-3-caching-strategy-적용)
6. [Day 4: 통합 테스트 & 성능 측정](#day-4-통합-테스트--성능-측정)
7. [실습 체크리스트](#실습-체크리스트)
8. [참고 자료](#참고-자료)

---

## 🎯 학습 목표

### STEP 11: Distributed Lock
- ✅ DB Lock의 한계를 이해하고 분산 환경에서의 동시성 제어 필요성 파악
- ✅ Redis를 이용한 분산락의 동작 원리 이해
- ✅ Simple Lock, Spin Lock, Pub/Sub 방식의 차이점 학습
- ✅ 락과 트랜잭션 순서 보장의 중요성 이해
- ✅ Redisson을 활용한 분산락 구현 및 통합 테스트 작성

### STEP 12: Caching
- ✅ 캐시의 필요성과 동작 원리 이해
- ✅ Memory Cache vs External Cache (Redis) 비교
- ✅ Cache-Aside, Read-Through 등 캐싱 패턴 학습
- ✅ Expiration/Eviction 전략 설계
- ✅ Cache Stampede 이슈 이해 및 대응 방안 수립
- ✅ 성능 개선 측정 및 보고서 작성

---

## 🔑 핵심 개념

### 1. 분산 환경에서의 동시성 제어

#### 왜 DB Lock만으로는 부족한가?

```
단일 서버 환경 (Week 3)
┌─────────────────┐
│   Application   │
│   (1 Instance)  │
└────────┬────────┘
         │
    ┌────▼────┐
    │   DB    │
    │  Lock   │
    └─────────┘

✅ synchronized, @Lock으로 해결 가능
```

```
분산 서버 환경 (Week 6)
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Application  │  │ Application  │  │ Application  │
│ Instance 1   │  │ Instance 2   │  │ Instance 3   │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       └─────────────────┼─────────────────┘
                         │
                    ┌────▼────┐
                    │   DB    │
                    │  Lock   │
                    └─────────┘

❌ DB Lock만으로는 여러 인스턴스 간 동시성 제어 불가
⚠️  각 인스턴스의 synchronized는 JVM 내부에서만 동작
⚠️  DB Connection Pool 고갈 위험
```

#### 분산락이 필요한 이유

| 문제 상황 | DB Lock의 한계 | 분산락 해결 방안 |
|---------|--------------|---------------|
| **다중 인스턴스** | synchronized는 단일 JVM에서만 동작 | Redis를 중앙 조정자로 사용 |
| **DB 부하** | 비관적 락은 DB Connection 점유 | Redis 기반으로 DB 접근 전 차단 |
| **트랜잭션 범위** | 트랜잭션 내에서만 락 유지 | 트랜잭션 범위를 넘어선 락 제어 |

### 2. Redis란?

**Redis** (REmote DIctionary Server)
- In-Memory Key-Value 저장소
- 초당 수만~수십만 건의 요청 처리 가능
- 원자적(Atomic) 연산 보장
- TTL(Time To Live) 지원

#### Redis가 분산락에 적합한 이유

```java
// Redis의 SETNX (SET if Not eXists) 명령어
SETNX lock:order:123 "instance-1"  // 성공 시 1 반환
SETNX lock:order:123 "instance-2"  // 실패 시 0 반환 (이미 존재)
```

✅ **원자성 보장**: SETNX는 단일 명령으로 "확인 + 설정"을 수행
✅ **빠른 속도**: 메모리 기반으로 밀리초 단위 응답
✅ **TTL 지원**: 락이 영구적으로 남지 않도록 자동 삭제

---

## 📅 Day 1: Redis 기초 & 동시성 복습

### 학습 시간: 2.5시간

### 1.1 동시성 문제 복습 (30분)

#### 지난 챕터에서 배운 Lock 전략

| Lock 종류 | 장점 | 단점 | 사용 시기 |
|----------|------|------|----------|
| **낙관적 Lock** | DB 부하 낮음 | 충돌 시 재시도 필요 | 수정 빈도 낮음 |
| **비관적 Lock** | 데이터 정합성 강력 | DB 부하 높음 | 수정 빈도 높음 |

#### 복습 퀴즈

```
Q1. 낙관적 Lock이 적합한 상황은?
A) 선착순 쿠폰 발급 (100명 동시 요청)
B) 사용자 프로필 수정 (개인별 독립적)
C) 좌석 예약 (동시 예약 가능성 높음)

정답: B - 충돌 가능성이 낮고 재시도가 허용되는 경우
```

```
Q2. 비관적 Lock의 문제점은?
A) 트랜잭션 범위가 길어지면 DB Connection 점유 증가
B) 버전 관리가 복잡함
C) 재시도 로직 구현 필요

정답: A - 락 대기 시간 동안 DB 커넥션 유지
```

### 1.2 Redis 기초 (1시간)

#### Redis 설치 (Docker 사용)

```yaml
# docker-compose.yml에 추가
services:
  redis:
    image: redis:7-alpine
    container_name: ecommerce-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
    networks:
      - ecommerce-network
```

```bash
# Redis 시작
docker-compose up -d redis

# Redis CLI 접속
docker exec -it ecommerce-redis redis-cli

# 기본 명령어 테스트
127.0.0.1:6379> PING
PONG

127.0.0.1:6379> SET mykey "Hello Redis"
OK

127.0.0.1:6379> GET mykey
"Hello Redis"

127.0.0.1:6379> DEL mykey
(integer) 1
```

#### 주요 명령어

| 명령어 | 설명 | 예시 |
|-------|------|------|
| `SET key value` | 키-값 저장 | `SET user:1 "john"` |
| `GET key` | 값 조회 | `GET user:1` |
| `SETNX key value` | 키가 없을 때만 저장 | `SETNX lock:user:1 "locked"` |
| `EXPIRE key seconds` | TTL 설정 | `EXPIRE lock:user:1 10` |
| `DEL key` | 키 삭제 | `DEL lock:user:1` |
| `TTL key` | 남은 시간 확인 | `TTL lock:user:1` |

#### Redis 자료구조

```
String (가장 기본)
┌─────────────────┐
│ key: "user:123" │
│ value: "Alice"  │
└─────────────────┘

Hash (객체 저장)
┌──────────────────────┐
│ key: "user:123"      │
│ fields:              │
│   name: "Alice"      │
│   age: "30"          │
│   city: "Seoul"      │
└──────────────────────┘

List (순서 보장)
┌──────────────────────┐
│ key: "queue:order"   │
│ values:              │
│   [0] "order:1"      │
│   [1] "order:2"      │
│   [2] "order:3"      │
└──────────────────────┘
```

### 1.3 Spring Boot + Redis 연동 (1시간)

#### Gradle 의존성 추가

```gradle
// build.gradle
dependencies {
    // Redis
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // Redisson (분산락용)
    implementation 'org.redisson:redisson-spring-boot-starter:3.23.5'
}
```

#### application.yml 설정

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

#### RedisConfig 설정

```java
package io.hhplus.ecommerce.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
```

#### 기본 동작 테스트

```java
package io.hhplus.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisService {

    private final RedissonClient redissonClient;

    public void set(String key, String value, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set(value, ttl);
    }

    public String get(String key) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    public boolean delete(String key) {
        return redissonClient.getBucket(key).delete();
    }
}
```

```java
// 테스트 코드
@SpringBootTest
class RedisServiceTest {

    @Autowired
    private RedisService redisService;

    @Test
    void Redis_기본_동작_테스트() {
        // Given
        String key = "test:key";
        String value = "Hello Redis";

        // When
        redisService.set(key, value, Duration.ofSeconds(10));
        String result = redisService.get(key);

        // Then
        assertThat(result).isEqualTo(value);

        // Cleanup
        redisService.delete(key);
    }
}
```

### 📝 Day 1 체크리스트

- [ ] 낙관적/비관적 Lock의 차이점을 설명할 수 있다
- [ ] Redis가 분산락에 적합한 이유를 3가지 이상 말할 수 있다
- [ ] Docker로 Redis를 실행하고 기본 명령어를 사용할 수 있다
- [ ] Spring Boot 프로젝트에 Redis를 연동하고 테스트를 통과했다
- [ ] SETNX 명령어의 원자성을 이해했다

---

## 📅 Day 2: Distributed Lock 구현

### 학습 시간: 3시간

### 2.1 분산락의 3가지 구현 방식 (1시간)

#### 1) Simple Lock (단순 락)

```java
public class SimpleLock {

    private final RedissonClient redissonClient;

    public void executeWithLock(String lockKey, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);

        // 락 획득 시도 (대기 안 함)
        boolean isLocked = lock.tryLock();

        if (isLocked) {
            try {
                task.run();  // 비즈니스 로직 실행
            } finally {
                lock.unlock();  // 반드시 해제
            }
        } else {
            throw new IllegalStateException("Lock 획득 실패");
        }
    }
}
```

**특징**
- ✅ 구현이 가장 간단
- ❌ 락 획득 실패 시 즉시 예외 발생
- ❌ 재시도 로직 없음
- 📌 사용 시나리오: 실패해도 괜찮은 경우 (좋아요, 조회수 증가 등)

#### 2) Spin Lock (재시도 락)

```java
public class SpinLock {

    private final RedissonClient redissonClient;
    private static final int MAX_RETRY = 10;
    private static final long WAIT_TIME_MS = 100;

    public void executeWithLock(String lockKey, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);

        int retryCount = 0;

        while (retryCount < MAX_RETRY) {
            boolean isLocked = lock.tryLock();

            if (isLocked) {
                try {
                    task.run();
                    return;  // 성공 시 종료
                } finally {
                    lock.unlock();
                }
            }

            // 재시도 대기
            retryCount++;
            try {
                Thread.sleep(WAIT_TIME_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("락 대기 중 인터럽트 발생", e);
            }
        }

        throw new IllegalStateException("최대 재시도 횟수 초과");
    }
}
```

**특징**
- ✅ 일정 시간 대기 후 재시도
- ❌ 많은 재시도로 네트워크 비용 증가
- ❌ CPU 리소스 낭비 가능
- 📌 사용 시나리오: 짧은 시간 내 락 해제가 예상되는 경우

#### 3) Pub/Sub Lock (권장)

```java
public class PubSubLock {

    private final RedissonClient redissonClient;

    public void executeWithLock(String lockKey, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 최대 10초 대기, 30초 후 자동 해제
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (isLocked) {
                try {
                    task.run();
                } finally {
                    lock.unlock();
                }
            } else {
                throw new IllegalStateException("Lock 획득 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트 발생", e);
        }
    }
}
```

**Redisson의 Pub/Sub 내부 동작**

```
Thread 1: 락 획득 시도
┌─────────────┐
│ tryLock()   │
│ SETNX 성공  │ ─────> Redis에 락 저장
└─────────────┘

Thread 2: 락 획득 시도
┌─────────────┐
│ tryLock()   │
│ SETNX 실패  │ ─────> Redis Subscribe (대기)
└─────────────┘

Thread 1: 락 해제
┌─────────────┐
│ unlock()    │
│ DEL + PUB   │ ─────> Redis에 해제 메시지 발행
└─────────────┘
                    ↓
Thread 2: 알림 받음
┌─────────────┐
│ Subscriber  │
│ 락 획득!    │ ─────> 비즈니스 로직 실행
└─────────────┘
```

**특징**
- ✅ 효율적인 대기 (CPU 낭비 없음)
- ✅ Redisson이 자동으로 Pub/Sub 관리
- ✅ 실무에서 가장 많이 사용
- 📌 사용 시나리오: 주문, 결제, 재고 차감 등 중요한 비즈니스 로직

### 2.2 락과 트랜잭션 순서의 중요성 (1시간)

#### ❌ 잘못된 예시 1: 트랜잭션 먼저 시작

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;

    @Transactional  // ❌ 트랜잭션이 먼저 시작됨
    public void createOrder(Long productId, int quantity) {
        // 1. 트랜잭션 시작 (DB Connection 획득)
        Product product = productRepository.findById(productId)
                .orElseThrow();

        // 2. 락 획득 시도 (이미 트랜잭션 진행 중)
        RLock lock = redissonClient.getLock("product:" + productId);

        try {
            lock.lock();

            // 3. 재고 차감 (문제 발생!)
            product.decreaseStock(quantity);

        } finally {
            lock.unlock();
        }
    }
}
```

**문제점**

```
시간 순서:
T0: Thread-1 트랜잭션 시작
T1: Thread-1 Product 조회 (재고: 10개)
T2: Thread-2 트랜잭션 시작
T3: Thread-2 Product 조회 (재고: 10개) ← 아직 Thread-1 커밋 전
T4: Thread-1 락 획득
T5: Thread-1 재고 차감 (10 - 5 = 5)
T6: Thread-1 락 해제
T7: Thread-1 커밋
T8: Thread-2 락 획득
T9: Thread-2 재고 차감 (10 - 5 = 5) ← 잘못된 값 사용!
T10: Thread-2 락 해제
T11: Thread-2 커밋

결과: 재고 5개 (정답: 0개)
```

#### ❌ 잘못된 예시 2: 락을 먼저 해제

```java
public void createOrder(Long productId, int quantity) {
    RLock lock = redissonClient.getLock("product:" + productId);

    try {
        lock.lock();

        // 트랜잭션 시작
        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow();
            product.decreaseStock(quantity);
        });

        lock.unlock();  // ❌ 트랜잭션 커밋 전에 락 해제

    } catch (Exception e) {
        lock.unlock();
        throw e;
    }
}
```

**문제점**

```
T0: Thread-1 락 획득
T1: Thread-1 트랜잭션 시작
T2: Thread-1 재고 차감 (10 - 5 = 5)
T3: Thread-1 락 해제 ← 아직 커밋 전!
T4: Thread-2 락 획득
T5: Thread-2 재고 조회 (10개) ← Thread-1 커밋 전이라 반영 안 됨
T6: Thread-2 재고 차감 (10 - 5 = 5)
T7: Thread-1 커밋 (재고 5개)
T8: Thread-2 커밋 (재고 5개) ← 덮어쓰기!

결과: 재고 5개 (정답: 0개)
```

#### ✅ 올바른 예시: 락 → 트랜잭션 → 커밋 → 락 해제

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final ProductRepository productRepository;

    public void createOrder(Long productId, int quantity) {
        String lockKey = "product:stock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 1. 락 획득 (10초 대기, 30초 후 자동 해제)
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (!isLocked) {
                throw new IllegalStateException("락 획득 실패");
            }

            // 2. 락 획득 후 트랜잭션 시작
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

                // 3. 재고 차감
                product.decreaseStock(quantity);

                // 4. 트랜잭션 커밋 (메서드 종료 시 자동)
            });

            // 5. 트랜잭션 커밋 완료 후 락 해제

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트", e);
        } finally {
            // 6. 반드시 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**올바른 순서**

```
┌────────────────────────────────────────┐
│ 1. Lock 획득                           │
│    RLock lock = redissonClient.getLock()│
│    lock.tryLock()                      │
└────────────────────────────────────────┘
                ↓
┌────────────────────────────────────────┐
│ 2. Transaction 시작                    │
│    transactionTemplate.execute()       │
└────────────────────────────────────────┘
                ↓
┌────────────────────────────────────────┐
│ 3. 비즈니스 로직 실행                   │
│    product.decreaseStock()             │
└────────────────────────────────────────┘
                ↓
┌────────────────────────────────────────┐
│ 4. Transaction Commit                  │
│    (메서드 종료 시 자동)                │
└────────────────────────────────────────┘
                ↓
┌────────────────────────────────────────┐
│ 5. Lock 해제                           │
│    lock.unlock()                       │
└────────────────────────────────────────┘
```

### 2.3 실제 구현: 주문 생성 시 분산락 적용 (1시간)

#### DistributedLockAspect 구현

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
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(io.hhplus.ecommerce.infrastructure.redis.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String lockKey = distributedLock.key();
        long waitTime = distributedLock.waitTime();
        long leaseTime = distributedLock.leaseTime();
        TimeUnit timeUnit = distributedLock.timeUnit();

        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(waitTime, leaseTime, timeUnit);

            if (!isLocked) {
                log.error("락 획득 실패: {}", lockKey);
                throw new IllegalStateException("락 획득 실패: " + lockKey);
            }

            log.info("락 획득 성공: {}", lockKey);
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("락 해제: {}", lockKey);
            }
        }
    }
}
```

#### DistributedLock 어노테이션

```java
package io.hhplus.ecommerce.infrastructure.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락의 이름 (Redis Key)
     */
    String key();

    /**
     * 락 획득을 위한 대기 시간 (기본 10초)
     */
    long waitTime() default 10L;

    /**
     * 락 임대 시간 (자동 해제, 기본 30초)
     */
    long leaseTime() default 30L;

    /**
     * 시간 단위
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
```

#### OrderService에 분산락 적용

```java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * 주문 생성 (분산락 적용)
     *
     * 락 획득 → 트랜잭션 시작 → 비즈니스 로직 → 커밋 → 락 해제
     */
    @DistributedLock(key = "'order:product:' + #productId")
    @Transactional
    public OrderResponse createOrder(Long productId, int quantity) {
        // 1. 상품 조회
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        // 2. 재고 차감 (동시성 제어됨)
        product.decreaseStock(quantity);

        // 3. 주문 생성
        Order order = Order.create(product, quantity);
        orderRepository.save(order);

        return OrderResponse.from(order);
    }
}
```

### 📝 Day 2 체크리스트

- [ ] Simple Lock, Spin Lock, Pub/Sub Lock의 차이를 설명할 수 있다
- [ ] 락과 트랜잭션 순서가 중요한 이유를 3가지 케이스로 설명할 수 있다
- [ ] DistributedLock 어노테이션을 구현하고 AOP로 적용할 수 있다
- [ ] 주문 생성 기능에 분산락을 적용하고 동작을 확인했다
- [ ] Redisson의 tryLock() 파라미터 (waitTime, leaseTime)의 역할을 이해했다

---

## 📅 Day 3: Caching Strategy 적용

### 학습 시간: 3시간

### 3.1 캐시의 필요성 (30분)

#### 캐시가 없을 때의 문제

```
100명의 사용자가 동시에 "인기 상품 조회" API 호출

Without Cache:
┌──────┐                           ┌──────┐
│ User │ ─────> GET /products/top  │ API  │
└──────┘                           └───┬──┘
                                       │
                                  100번 쿼리 실행
                                       │
                                  ┌────▼────┐
                                  │  MySQL  │
                                  └─────────┘

문제:
- DB Connection Pool 고갈
- Slow Query 반복 실행
- 응답 시간 증가 (500ms → 2초)
```

#### 캐시 적용 후

```
With Cache:
┌──────┐                           ┌──────┐
│ User │ ─────> GET /products/top  │ API  │
└──────┘                           └───┬──┘
                                       │
                                  Cache Hit?
                                       │
                              Yes ─────┼───── No
                                       │      │
                                  ┌────▼──┐   │
                                  │ Redis │   │
                                  └───────┘   │
                                              │
                                         ┌────▼────┐
                                         │  MySQL  │
                                         └─────────┘

결과:
- 99개 요청은 Redis에서 즉시 응답 (1~5ms)
- 1개 요청만 DB 조회 후 Redis에 저장
- 응답 시간 95% 감소
```

### 3.2 캐싱 패턴 (1시간)

#### 1) Cache-Aside (Look-Aside) 패턴

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    public List<ProductResponse> getPopularProducts() {
        String cacheKey = "popular:products";

        // 1. 캐시 조회
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            // 2. Cache Hit - 즉시 반환
            return cached;
        }

        // 3. Cache Miss - DB 조회
        List<Product> products = productRepository.findPopularProducts();
        List<ProductResponse> response = products.stream()
                .map(ProductResponse::from)
                .toList();

        // 4. 캐시 저장 (TTL: 5분)
        bucket.set(response, Duration.ofMinutes(5));

        return response;
    }
}
```

**동작 흐름**

```
1. 애플리케이션이 먼저 캐시 확인
   ↓
2-a. Cache Hit → 즉시 반환
   OR
2-b. Cache Miss → DB 조회 → 캐시 저장 → 반환
```

**특징**
- ✅ 구현이 간단
- ✅ 캐시 장애 시에도 서비스 정상 동작
- ❌ 첫 요청은 항상 느림 (Cache Miss)
- 📌 가장 많이 사용되는 패턴

#### 2) Read-Through 패턴

```java
@Service
public class ProductService {

    @Cacheable(value = "popular:products", key = "'top5'")
    public List<ProductResponse> getPopularProducts() {
        // Spring Cache가 자동으로 처리:
        // 1. 캐시 확인
        // 2. Cache Miss 시 메서드 실행
        // 3. 결과를 캐시에 저장

        return productRepository.findPopularProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }
}
```

**특징**
- ✅ 코드가 더 간결 (Spring Cache 자동 처리)
- ✅ 캐시 로직과 비즈니스 로직 분리
- ❌ 프레임워크 의존성 증가
- 📌 Spring 프로젝트에서 권장

#### 3) Write-Through vs Write-Behind

```java
// Write-Through: 쓰기 시 캐시와 DB 동시 갱신
@CachePut(value = "product", key = "#product.id")
public Product updateProduct(Product product) {
    // 1. DB 업데이트
    productRepository.save(product);

    // 2. 캐시도 자동 갱신 (@CachePut)
    return product;
}

// Write-Behind: 쓰기 시 캐시만 갱신, DB는 비동기
public void updateProductAsync(Product product) {
    // 1. 캐시만 즉시 갱신
    redisTemplate.opsForValue().set("product:" + product.getId(), product);

    // 2. DB는 나중에 배치로 갱신 (별도 스케줄러)
    eventPublisher.publish(new ProductUpdateEvent(product));
}
```

### 3.3 Expiration vs Eviction (30분)

#### Expiration (만료)

```java
// TTL 기반 자동 삭제
@Cacheable(value = "product", key = "#productId")
@CacheExpire(ttl = 300) // 5분 후 자동 삭제
public Product getProduct(Long productId) {
    return productRepository.findById(productId)
            .orElseThrow();
}
```

```bash
# Redis에서 TTL 확인
redis> SET popular:products "data" EX 300
redis> TTL popular:products
(integer) 298  # 남은 시간 (초)
```

#### Eviction (명시적 삭제)

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    @CacheEvict(value = "popular:products", allEntries = true)
    public void refreshPopularProducts() {
        // 캐시 전체 삭제
        // 다음 조회 시 최신 데이터 캐싱
    }

    @CacheEvict(value = "product", key = "#productId")
    public void updateProduct(Long productId, ProductRequest request) {
        // 특정 상품 캐시만 삭제
        Product product = productRepository.findById(productId)
                .orElseThrow();
        product.update(request);
        productRepository.save(product);
    }
}
```

**Eviction 전략**

| 전략 | 설명 | 사용 시점 |
|-----|------|----------|
| **allEntries=true** | 캐시 전체 삭제 | 대량 데이터 변경 |
| **key 지정** | 특정 키만 삭제 | 개별 데이터 수정 |
| **@CacheEvict + @Scheduled** | 주기적 삭제 | 배치 갱신 |

### 3.4 Cache Stampede 이슈와 해결 (1시간)

#### Cache Stampede란?

```
상황: 인기 상품 캐시가 만료되는 순간 100명이 동시 요청

Without 대응:
T0: 캐시 만료
T1: 100개 요청 동시 도착
T2: 모두 Cache Miss
T3: 100개 DB 쿼리 동시 실행 ← DB 부하 폭증!
T4: 100개 캐시 저장 (중복)

문제:
- DB Connection Pool 고갈
- 응답 시간 급증
- 서버 다운 위험
```

#### 해결 방법 1: 분산락 + 캐시

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;

    public List<ProductResponse> getPopularProducts() {
        String cacheKey = "popular:products";
        String lockKey = "lock:popular:products";

        // 1. 캐시 조회
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            return cached;  // Cache Hit
        }

        // 2. Cache Miss - 락 획득 시도
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 3. 락 획득 (최대 5초 대기)
            boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (isLocked) {
                // 4. Double-Check: 락 대기 중 다른 스레드가 캐싱했을 수 있음
                cached = bucket.get();
                if (cached != null) {
                    return cached;
                }

                // 5. DB 조회 (1개 스레드만 실행)
                List<Product> products = productRepository.findPopularProducts();
                List<ProductResponse> response = products.stream()
                        .map(ProductResponse::from)
                        .toList();

                // 6. 캐시 저장
                bucket.set(response, Duration.ofMinutes(5));

                return response;
            } else {
                // 7. 락 획득 실패 - 잠시 대기 후 재시도
                Thread.sleep(100);
                return getPopularProducts();  // 재귀 호출
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**동작 흐름**

```
T0: 100개 요청 동시 도착 (캐시 만료 상태)
T1: Thread-1 락 획득 성공
T2: Thread-2~100 락 대기 (Pub/Sub으로 효율적 대기)
T3: Thread-1 DB 조회 및 캐시 저장
T4: Thread-1 락 해제
T5: Thread-2 락 획득 → Double-Check → 캐시 Hit! (DB 조회 안 함)
...

결과: DB 쿼리 1번만 실행
```

#### 해결 방법 2: Soft/Hard TTL

```java
public class SmartCache {

    private static final Duration SOFT_TTL = Duration.ofMinutes(5);  // 실제 만료
    private static final Duration HARD_TTL = Duration.ofMinutes(6);  // 백업 데이터

    public List<ProductResponse> getPopularProducts() {
        // 1. Soft TTL 캐시 조회
        List<ProductResponse> softCache = getSoftCache();

        if (softCache != null) {
            return softCache;
        }

        // 2. Soft TTL 만료 - 비동기로 갱신 시작
        CompletableFuture.runAsync(this::refreshCache);

        // 3. Hard TTL 캐시 반환 (약간 오래된 데이터지만 즉시 응답)
        List<ProductResponse> hardCache = getHardCache();

        if (hardCache != null) {
            return hardCache;
        }

        // 4. Hard TTL도 만료 - 동기 조회
        return refreshCacheSync();
    }
}
```

### 3.5 실제 구현: 인기 상품 조회 캐싱 (30분)

```java
package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;

    /**
     * 인기 상품 조회 (Cache-Aside 패턴 + 분산락)
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getPopularProducts() {
        String cacheKey = "popular:products:top5";

        // 1. 캐시 조회
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            log.info("캐시 Hit: {}", cacheKey);
            return cached;
        }

        log.info("캐시 Miss: {} - DB 조회 시작", cacheKey);

        // 2. Cache Miss - 분산락으로 DB 조회 중복 방지
        return getPopularProductsWithLock(cacheKey);
    }

    @DistributedLock(key = "'lock:popular:products'", waitTime = 5, leaseTime = 10)
    private List<ProductResponse> getPopularProductsWithLock(String cacheKey) {
        // Double-Check: 락 대기 중 다른 스레드가 캐싱했을 수 있음
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        if (cached != null) {
            log.info("Double-Check 캐시 Hit: {}", cacheKey);
            return cached;
        }

        // DB 조회
        List<Product> products = productRepository.findTop5ByOrderBySalesCountDesc();
        List<ProductResponse> response = products.stream()
                .map(ProductResponse::from)
                .toList();

        // 캐시 저장 (TTL: 5분)
        bucket.set(response, Duration.ofMinutes(5));
        log.info("캐시 저장 완료: {} (TTL: 5분)", cacheKey);

        return response;
    }

    /**
     * 인기 상품 캐시 갱신 (Scheduled)
     */
    @Scheduled(cron = "0 */10 * * * *")  // 10분마다 실행
    public void refreshPopularProductsCache() {
        String cacheKey = "popular:products:top5";

        log.info("인기 상품 캐시 갱신 시작");

        List<Product> products = productRepository.findTop5ByOrderBySalesCountDesc();
        List<ProductResponse> response = products.stream()
                .map(ProductResponse::from)
                .toList();

        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        bucket.set(response, Duration.ofMinutes(5));

        log.info("인기 상품 캐시 갱신 완료: {} (TTL: 5분)", cacheKey);
    }
}
```

### 📝 Day 3 체크리스트

- [ ] Cache-Aside와 Read-Through 패턴의 차이를 설명할 수 있다
- [ ] Expiration과 Eviction의 차이를 이해하고 적절히 사용할 수 있다
- [ ] Cache Stampede 이슈를 설명하고 해결 방법을 2가지 이상 말할 수 있다
- [ ] 인기 상품 조회 API에 캐싱을 적용하고 동작을 확인했다
- [ ] Double-Check 패턴의 필요성을 이해했다

---

## 📅 Day 4: 통합 테스트 & 성능 측정

### 학습 시간: 1.5시간

### 4.1 TestContainers로 Redis 통합 테스트 (1시간)

#### Gradle 의존성 추가

```gradle
dependencies {
    // TestContainers
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
    testImplementation 'org.testcontainers:mysql:1.19.3'

    // Redis TestContainer 추가
    testImplementation 'com.redis.testcontainers:testcontainers-redis:1.6.4'
}
```

#### TestContainers 설정

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
                .withDatabaseName("ecommerce_test")
                .withUsername("test")
                .withPassword("test");
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", "128mb");
    }
}
```

#### 분산락 통합 테스트

```java
package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
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
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        Product product = Product.builder()
                .id(1L)
                .name("테스트 상품")
                .price(10000L)
                .stock(100)
                .build();

        productRepository.save(product);
    }

    @Test
    void 분산락_동시성_테스트_100명이_동시주문() throws InterruptedException {
        // Given
        Long productId = 1L;
        int threadCount = 100;
        int quantityPerOrder = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 100명이 동시에 주문 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderService.createOrder(productId, quantityPerOrder);
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

        // Then: 정확히 100개 주문 성공, 재고 0개
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    void 분산락_동시성_테스트_재고부족_케이스() throws InterruptedException {
        // Given: 재고 50개
        Long productId = 1L;
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStock(50);
        productRepository.save(product);

        int threadCount = 100;
        int quantityPerOrder = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 100명이 동시 주문 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderService.createOrder(productId, quantityPerOrder);
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

        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }
}
```

#### 캐시 통합 테스트

```java
package io.hhplus.ecommerce.application.product;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestContainersConfig.class)
class ProductServiceCacheTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void 인기상품_캐시_테스트() {
        // Given
        String cacheKey = "popular:products:top5";

        // When: 첫 번째 호출 (Cache Miss, DB 조회)
        List<ProductResponse> firstCall = productService.getPopularProducts();

        // Then: 캐시에 저장되었는지 확인
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        List<ProductResponse> cached = bucket.get();

        assertThat(cached).isNotNull();
        assertThat(cached).hasSize(firstCall.size());

        // When: 두 번째 호출 (Cache Hit)
        List<ProductResponse> secondCall = productService.getPopularProducts();

        // Then: 동일한 데이터 반환
        assertThat(secondCall).isEqualTo(firstCall);
    }

    @Test
    void 캐시_TTL_테스트() throws InterruptedException {
        // Given
        String cacheKey = "popular:products:top5";

        // When: 캐시 저장
        productService.getPopularProducts();

        // Then: TTL 확인 (약 5분 = 300초)
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        long ttl = bucket.remainTimeToLive();  // 밀리초 단위

        assertThat(ttl).isGreaterThan(290_000);  // 최소 290초
        assertThat(ttl).isLessThanOrEqualTo(300_000);  // 최대 300초
    }

    @Test
    void 캐시_Stampede_방지_테스트() throws InterruptedException {
        // Given
        String cacheKey = "popular:products:top5";

        // 캐시 삭제 (만료 상태 시뮬레이션)
        redissonClient.getBucket(cacheKey).delete();

        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        AtomicInteger dbQueryCount = new AtomicInteger(0);

        // When: 50명이 동시에 호출
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    productService.getPopularProducts();
                    // DB 쿼리 카운트는 로그나 AOP로 측정
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 분산락 덕분에 DB 쿼리는 1번만 실행되어야 함
        // (실제로는 로깅이나 메트릭으로 확인)
        RBucket<List<ProductResponse>> bucket = redissonClient.getBucket(cacheKey);
        assertThat(bucket.get()).isNotNull();
    }
}
```

### 4.2 성능 측정 보고서 작성 (30분)

#### 측정 항목

```markdown
# STEP 12 성능 개선 보고서

## 1. 배경

### 문제 상황
- 인기 상품 조회 API가 느림 (평균 500ms)
- DB Connection Pool 부족 (HikariCP Max: 10)
- 동시 요청 증가 시 응답 시간 급증 (2초 이상)

### 원인 분석
- 복잡한 쿼리 (JOIN 3개, ORDER BY, LIMIT)
- 매 요청마다 DB 조회 (캐시 미적용)
- 동시 요청 시 DB 부하 증가

## 2. 해결 방안

### 적용한 캐싱 전략
- **패턴**: Cache-Aside
- **저장소**: Redis (Redisson)
- **TTL**: 5분
- **갱신**: @Scheduled (10분마다)
- **Stampede 방지**: 분산락 적용

### 구현 코드
```java
@DistributedLock(key = "'lock:popular:products'")
private List<ProductResponse> getPopularProductsWithLock(String cacheKey) {
    // Double-Check 패턴
    // DB 조회
    // 캐시 저장 (TTL 5분)
}
```

## 3. 성능 측정 결과

### 측정 환경
- Tool: JMeter
- 동시 사용자: 100명
- 총 요청 수: 1,000회
- Ramp-up: 10초

### Before (캐시 미적용)

| 지표 | 값 |
|-----|---|
| 평균 응답 시간 | 523ms |
| 최대 응답 시간 | 2,145ms |
| TPS | 48 req/s |
| 에러율 | 3.2% |
| DB Connection 사용률 | 95% |

### After (캐시 적용)

| 지표 | 값 | 개선율 |
|-----|---|--------|
| 평균 응답 시간 | 12ms | **95.7% 감소** |
| 최대 응답 시간 | 45ms | **97.9% 감소** |
| TPS | 320 req/s | **566% 증가** |
| 에러율 | 0% | **100% 감소** |
| DB Connection 사용률 | 15% | **84% 감소** |

### 그래프
```
응답 시간 비교 (ms)
Before: ████████████████████ 523ms
After:  █ 12ms
```

## 4. Cache Hit Rate

### 측정 결과
- Cache Hit: 98.7%
- Cache Miss: 1.3%

### 분석
- 첫 요청과 TTL 만료 시에만 Cache Miss
- 10분마다 Scheduled로 갱신하여 만료 최소화
- 분산락으로 동시 Miss 시 DB 쿼리 1번만 실행

## 5. 한계점 및 개선 방안

### 한계점
1. **TTL 기반 만료**: 데이터 변경 시 최대 5분간 지연
2. **Redis 장애 시**: 서비스 전체 영향 (Fallback 없음)
3. **메모리 사용량**: 대용량 데이터 캐싱 시 Redis 메모리 부족 가능

### 개선 방안
1. **Event-Driven Cache Invalidation**
   - 상품 수정 시 캐시 즉시 삭제 (@CacheEvict)
   - Kafka로 캐시 갱신 이벤트 발행

2. **Redis Cluster 구성**
   - Master-Slave 복제
   - Sentinel을 통한 자동 Failover

3. **Soft/Hard TTL 적용**
   - Soft TTL 만료 시 비동기 갱신
   - Hard TTL까지는 이전 데이터 제공

## 6. 결론

### 성과
- 응답 시간 95.7% 감소
- DB 부하 84% 감소
- 에러율 0% 달성

### 학습 내용
- Cache-Aside 패턴의 효과적 적용
- 분산락을 통한 Cache Stampede 방지
- Double-Check 패턴의 필요성 이해
```

### 📝 Day 4 체크리스트

- [ ] TestContainers로 Redis 통합 테스트를 작성하고 통과했다
- [ ] 동시성 테스트로 분산락이 정상 동작함을 검증했다
- [ ] 캐시 TTL과 Hit/Miss를 테스트로 확인했다
- [ ] 성능 측정 결과를 보고서로 작성했다 (Before/After 비교)
- [ ] 개선 효과를 수치로 표현할 수 있다 (응답 시간, TPS 등)

---

## 🎯 전체 실습 체크리스트

### STEP 11: Distributed Lock

#### 필수 구현
- [ ] Redis와 Redisson 연동
- [ ] DistributedLock 어노테이션 구현
- [ ] AOP로 분산락 적용
- [ ] 주문 생성 기능에 분산락 적용
- [ ] 결제 기능에 분산락 적용
- [ ] 쿠폰 발급 기능에 분산락 적용

#### 테스트
- [ ] 분산락 동시성 테스트 (100명 동시 요청)
- [ ] 재고 부족 케이스 테스트
- [ ] 락 타임아웃 테스트
- [ ] 락과 트랜잭션 순서 검증

#### 문서화
- [ ] 분산락이 필요한 이유 설명
- [ ] Simple/Spin/Pub-Sub Lock 비교
- [ ] 락과 트랜잭션 순서 중요성 문서화

### STEP 12: Caching

#### 필수 구현
- [ ] Redis 캐시 설정
- [ ] 인기 상품 조회 캐싱 적용
- [ ] Cache-Aside 패턴 구현
- [ ] 분산락으로 Cache Stampede 방지
- [ ] @Scheduled로 주기적 캐시 갱신
- [ ] TTL 설정 (5분)

#### 테스트
- [ ] 캐시 Hit/Miss 테스트
- [ ] TTL 동작 테스트
- [ ] Cache Stampede 방지 테스트 (50명 동시 요청)
- [ ] 성능 측정 (Before/After)

#### 성능 보고서
- [ ] 문제 배경 및 원인 분석
- [ ] 적용한 캐싱 전략 설명
- [ ] 성능 측정 결과 (응답 시간, TPS, DB 부하)
- [ ] Cache Hit Rate 분석
- [ ] 한계점 및 개선 방안
- [ ] 결론 및 학습 내용 정리

---

## 📚 참고 자료

### 공식 문서
- [Redis 공식 문서](https://redis.io/docs/)
- [Redisson GitHub](https://github.com/redisson/redisson)
- [Spring Cache 가이드](https://spring.io/guides/gs/caching/)

### 추천 아티클
- [분산 락을 구현하는 여러 가지 방법](https://www.youtube.com/watch?v=UOWy6zdsD-c) (우아한테크 세미나)
- [Redis를 이용한 분산 락 구현](https://hyperconnect.github.io/2019/11/15/redis-distributed-lock-1.html)
- [Cache Stampede 문제와 해결](https://www.sobyte.net/post/2022-01/cache-stampede/)

### 도서
- 『Redis 핵심 정리』
- 『가상 면접 사례로 배우는 대규모 시스템 설계 기초』 (Ch 8. 캐시)

---

## 💡 자주 묻는 질문 (FAQ)

### Q1. synchronized와 분산락의 차이는?
```
synchronized:
- JVM 레벨 락 (단일 인스턴스에서만 동작)
- 빠르지만 분산 환경에서 무용지물

분산락 (Redis):
- 여러 인스턴스 간 공유되는 락
- 네트워크 비용 있지만 분산 환경 필수
```

### Q2. Redisson vs Lettuce 어떤 걸 쓰나요?
```
Lettuce:
- Spring Data Redis 기본 클라이언트
- 저수준 API (직접 구현 필요)

Redisson:
- 고수준 API (RLock, RBucket 등 제공)
- 분산락 기능 내장
- 실무에서 더 많이 사용
```

### Q3. TTL을 얼마로 설정해야 하나요?
```
고려 사항:
1. 데이터 변경 빈도 (자주 변하면 짧게)
2. 데이터 중요도 (정확성 중요하면 짧게)
3. DB 부하 (부하 높으면 길게)

일반적인 값:
- 인기 상품: 5~10분
- 사용자 정보: 30분~1시간
- 설정 데이터: 1시간 이상
```

### Q4. Cache Stampede를 막으려면 항상 분산락을 써야 하나요?
```
아니요! 다른 방법도 있습니다:

1. Soft/Hard TTL (추천)
2. Probabilistic Early Expiration
3. Cache Warming (미리 캐시 채우기)
4. Refresh-Ahead

분산락은 가장 확실하지만 복잡도가 높아요.
```

### Q5. 캐시와 DB 데이터가 불일치하면 어떻게 하나요?
```
전략:

1. 짧은 TTL 설정
2. 데이터 수정 시 @CacheEvict로 즉시 삭제
3. Event-Driven 캐시 갱신
4. 읽기 전용 데이터에만 캐시 적용

완벽한 일관성은 불가능합니다.
"Eventual Consistency" (최종적 일관성) 수용!
```

---

**🎉 STEP 11-12 학습을 완료하셨습니다!**

이제 분산 환경에서 동시성 제어와 성능 최적화를 자신 있게 적용할 수 있습니다!
