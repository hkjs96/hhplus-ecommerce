# Week 7 제이 코치님 멘토링 핵심 요약

**일시:** 2025.12.02 화 오후 9:58
**코치:** 제이 코치님
**주제:** Redis 실무 적용, 성능 최적화, 모니터링

---

## 🎯 핵심 질의응답

### 1. 동점 처리 방식 (혜영님)

**질문:**
> Redis Sorted Set에서 동점(Score 동률)일 때 어떤 방식으로 처리하는 것이 좋은가요?
> 타임스탬프를 score나 member에 추가하여 먼저 주문한 것을 우선으로 가져가는 방법도 있을 것 같은데, 비즈니스 로직에 동률 계산이 큰 비중이 없다면 기존 사전순으로 정렬하는 것도 문제는 없을 것 같습니다.

**제이 코치 답변:**
> 동점 처리 방식은 **비즈니스 요구사항에 따라 결정**하시면 됩니다.
> 이커머스 인기상품 랭킹에서는 대부분 동점 우선순위가 크게 중요하지 않기 때문에 **기본 사전순 정렬로 두어도 문제없습니다**.
> 만약 "먼저 달성한 쪽이 우선"같은 요구가 있다면 **score에 타임스탬프를 소수점으로 병합하는 방식**이 일반적입니다.

**참고 자료:**
- [우아한형제들 - Redis 활용 사례](https://techblog.woowahan.com/2601/)
- [Redis ZADD 공식 문서](https://redis.io/docs/latest/commands/zadd/)

**핵심 포인트:**
- 동점 우선순위가 중요하지 않으면 → **사전순(lexicographical) 정렬**
- 먼저 달성한 쪽 우선이면 → **score + 타임스탬프 병합**

---

### 2. 쿠폰 발급 시 Redis vs DB 순서 (혜영님)

**질문:**
> 쿠폰 발급 시 Redis에서 바로 발급 완료 상태로 기록할지, 아니면 예약(Pending) 상태로 먼저 기록하고 DB 저장 성공 후 발급 완료로 변경할지 고민입니다.
> TransactionalEventListener를 사용하여 DB commit 완료된 후 Redis 호출을 해도 될 것 같다는 생각을 했습니다.

**제이 코치 답변:**
> **TransactionalEventListener 방식이 정답에 가깝습니다.**
> `@TransactionalEventListener(phase = AFTER_COMMIT)` DB 트랜잭션 커밋 후에 Redis 처리하면 **롤백 걱정이 사라지고 구현도 단순해집니다**.
> 다만 Redis 호출 실패 시 재처리 로직은 별도로 고려해야 하는데, 이때 핵심 원칙은 **"Redis는 성능 최적화용이고 DB가 진짜 원장"**이라는 점입니다.

**선택지 비교:**

| 방식 | 장점 | 단점 |
|------|------|------|
| **Redis 먼저, DB 나중** | 구현 단순 | DB 실패 시 Redis 롤백 필요 |
| **Pending 상태 도입** | 명확한 상태 관리 | 상태 관리 복잡, Pending 무한 대기 위험 |
| **DB 먼저, Redis 나중 (권장)** | 롤백 불필요, DB 원장 보존 | Redis 실패 시 1차 체크 실패 (2차 DB 체크로 보완) |

**✅ 권장 구현 방식:**
```java
@Transactional
public void issueCoupon(Long userId, Long couponId) {
    // 1. DB에 먼저 저장 (트랜잭션 내)
    CouponIssue issue = couponIssueRepository.save(
        CouponIssue.create(userId, couponId)
    );

    // 2. 트랜잭션 커밋 후 Redis 갱신
    eventPublisher.publishEvent(new CouponIssuedEvent(userId, couponId));
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handleCouponIssued(CouponIssuedEvent event) {
    try {
        // Redis에 발급 기록
        redisTemplate.opsForSet().add(
            "coupon:" + event.getCouponId() + ":issued",
            event.getUserId()
        );
    } catch (Exception e) {
        // 실패 시 로그만 남기고, DB에는 이미 기록되어 있음
        log.error("Redis 발급 기록 실패", e);
    }
}

// 쿠폰 발급 API (방어 로직)
public boolean canIssueCoupon(Long userId, Long couponId) {
    // 1차: Redis 중복 체크 (빠름)
    Boolean existsInRedis = redisTemplate.opsForSet()
        .isMember("coupon:" + couponId + ":issued", userId);

    if (Boolean.TRUE.equals(existsInRedis)) {
        return false;
    }

    // 2차: DB 중복 체크 (Redis 실패 시 대비)
    return !couponIssueRepository.existsByUserIdAndCouponId(userId, couponId);
}
```

**참고 자료:**
- [Spring TransactionalEventListener 공식 문서](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [우아한형제들 - 이벤트 기반 아키텍처](https://techblog.woowahan.com/7835/)

**핵심 포인트:**
- DB가 원장(Source of Truth), Redis는 성능 최적화용
- Redis 장애 시에도 서비스 정상 동작 보장

---

### 3. 분산락 성능 이슈 (대원님)

**질문:**
> 분산 락(Redisson)을 사용하면 데이터 정합성은 보장되지만, 트래픽이 몰릴 경우 락 대기 시간으로 인해 처리량이 저하될 우려가 있어 보입니다.

**제이 코치 답변:**
> 분산락은 **동시성 제어가 필수인 최소 구간에만 적용**해야 합니다.
> 쿠폰 재고 차감 같은 경우 락 대신 **Redis INCR/DECR의 원자성을 활용하면 락 없이 처리 가능**하고요.
> 락이 필수라면 **락 범위를 최소화**하고, **락 획득 대기시간(waitTime)을 짧게 설정**하여 빠른 실패를 유도합니다.
> **락 안에서 외부 API 호출이나 긴 작업을 하면 안 됩니다**.

**❌ 잘못된 방식:**
```java
@Transactional
public void issueCoupon(Long couponId, Long userId) {
    RLock lock = redissonClient.getLock("coupon:" + couponId);

    try {
        lock.lock();  // 모든 사용자가 순차 대기 → 병목 발생

        // 사용자 검증 (불필요하게 락 안에서 처리)
        User user = userRepository.findById(userId);
        if (!user.isActive()) throw new Exception();

        // 재고 차감
        Coupon coupon = couponRepository.findById(couponId);
        coupon.decreaseQuantity();

        // 외부 API 호출 (락 안에서 하면 안 됨!)
        notificationService.send(userId, "쿠폰 발급 완료");

    } finally {
        lock.unlock();
    }
}
```

**✅ 올바른 방식 (Redis INCR/DECR 활용):**
```java
public CouponIssueResult issueCoupon(Long couponId, Long userId) {
    String remainKey = "coupon:" + couponId + ":remain";
    String issuedKey = "coupon:" + couponId + ":issued";

    // 1. 재고 차감 (원자적, 락 불필요)
    Long remain = redisTemplate.opsForValue().decrement(remainKey);

    // 2. 재고 부족 체크
    if (remain < 0) {
        // 원복
        redisTemplate.opsForValue().increment(remainKey);
        return CouponIssueResult.soldOut();
    }

    // 3. 발급 기록 (중복 방지)
    Long addResult = redisTemplate.opsForSet().add(issuedKey, userId);
    if (addResult == 0) {
        // 이미 발급됨 → 원복
        redisTemplate.opsForValue().increment(remainKey);
        return CouponIssueResult.alreadyIssued();
    }

    // 4. 비동기로 알림 전송 (락 밖에서)
    eventPublisher.publishEvent(new CouponIssuedEvent(userId, couponId));

    return CouponIssueResult.success();
}
```

**참고 자료:**
- [컬리 - 분산 락 활용 (Redisson)](https://helloworld.kurly.com/blog/distributed-redisson-lock/)

**핵심 포인트:**
- 락 대신 **원자적 연산(INCR/DECR)** 활용
- 락 필수 시 **최소 범위만 잠금**
- **waitTime 짧게 설정** (빠른 실패)
- **락 안에서 외부 API 호출 금지**

---

### 4. 선착순 쿠폰 발급 Queue 처리 (지수님)

**질문:**
> 선착순 쿠폰 발급에 있어서 선착순 쿠폰 발급 요청이 실패했다고 해서 먼저 요청온 것이 취소하면 안되는데, Queue를 이용한다고 해도 한번 읽고 지워진다고 들었습니다.
> DLQ 같은 형태를 구성하지 않고 방법이 존재하는 것일까요?

**제이 코치 답변:**
> DLQ 없어도 해결이 가능합니다.
> **선착순 발급과 발급 처리를 분리**하시면 됩니다.
> 사용자 입장에서는 제일 중요한 건 "쿠폰이 내 계정에 들어왔는지"인데, 사실 **쿠폰이 발급되는 부분은 나중에 해도 됩니다**.
> 왜냐하면 선착순 판정만 확실하게 되면 되니까요. **100번째 안에 들었다**라고 하는 부분은 뒤집히지 않는 사실이니까 이 부분은 뒤집힐 일이 없으니까 제일 이게 제일 중요하겠죠.

**문제 상황:**
```
1. 선착순 쿠폰 발급 요청 → Queue에 들어감
2. Consumer가 메시지 읽음
3. 처리 중 실패 발생
4. 메시지는 Queue에서 이미 삭제됨 (Commit 완료)
5. 먼저 요청한 사람의 쿠폰 발급 취소됨 ❌
```

**✅ 해결 방법: 선착순 판정 vs 발급 처리 분리**

```java
// 1단계: 선착순 판정 (Redis INCR로 순번 확정)
public CouponQueueResult registerQueue(Long couponId, Long userId) {
    String queueKey = "coupon:" + couponId + ":queue";

    // 원자적으로 순번 부여 (뒤집히지 않는 사실 확정)
    Long sequence = redisTemplate.opsForValue().increment(queueKey);

    if (sequence > 100) {
        return CouponQueueResult.soldOut();
    }

    // Kafka에 발급 요청 전송 (나중에 처리)
    kafkaTemplate.send("coupon-issue-topic",
        new CouponIssueMessage(userId, couponId, sequence)
    );

    return CouponQueueResult.queued(sequence);  // "당신은 N번째입니다"
}

// 2단계: 발급 처리 (멱등성 보장)
@KafkaListener(topics = "coupon-issue-topic")
public void processCouponIssue(CouponIssueMessage message) {
    String issuedKey = "coupon:" + message.getCouponId() + ":issued";

    // 중복 체크 (멱등성)
    Boolean alreadyIssued = redisTemplate.opsForSet()
        .isMember(issuedKey, message.getUserId());

    if (Boolean.TRUE.equals(alreadyIssued)) {
        // 이미 발급됨 → 재시도 성공 (멱등성)
        return;
    }

    try {
        // DB에 쿠폰 발급 기록
        couponIssueRepository.save(
            CouponIssue.create(message.getUserId(), message.getCouponId())
        );

        // Redis에 발급 기록
        redisTemplate.opsForSet().add(issuedKey, message.getUserId());

        // Kafka Commit (성공)
        acknowledgment.acknowledge();

    } catch (Exception e) {
        // 실패 시 재시도 (Kafka는 메시지 유지)
        log.error("쿠폰 발급 실패, 재시도 예정", e);
        throw e;  // Kafka가 재시도
    }
}
```

**Kafka 설정 (매뉴얼 커밋):**
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # 수동 커밋
      max-poll-records: 10
    listener:
      ack-mode: manual  # 명시적 Acknowledge
```

**DLQ는 언제 사용하나?**
- 데이터 형식이 잘못된 경우 (JSON 파싱 실패)
- 존재하지 않는 유저 ID
- 비즈니스 로직상 처리 불가능한 경우
- **재시도해도 계속 실패하는 경우** → DLQ로 이동 후 수동 처리

**핵심 포인트:**
- **선착순 판정 (INCR)**: 뒤집히지 않는 사실 확정
- **발급 처리**: 나중에 해도 됨 (멱등성 보장)
- **Kafka 매뉴얼 커밋**: 처리 성공 시에만 Commit
- DLQ는 재시도 불가능한 메시지용

---

### 5. 실시간 랭킹 DIP 적용 (혜영님)

**질문:**
> 발제에 `인프라 변경(DIP 적용 등)에 따른 비즈니스 로직 보호 및 의존성 관리의 적절성`이 있는데, 실시간 랭킹은 중요한 정보가 아니라서 캐시로만 사용이 될 것 같습니다.
> 캐시 말고 다른 것으로 대체가 안 될 것 같은데 이런 것도 DIP를 고려해야하는지 고민이 되었습니다.

**제이 코치 답변:**
> **DIP 적용을 권장합니다.**
> 현재 Redis 캐시만 사용하더라도 RankingReader 같은 인터페이스를 두면 **단위 테스트 시 Mock 처리가 훨씬 쉬워지고**, 향후 요구사항 변경 시에도 대응할 수 있습니다.
> 실제로 **"Redis 장애 시 랭킹이 아예 안 보이는 문제"**가 생겨서 **로컬 캐시와 Redis 2단 캐시**로 전환하는 경우가 있는데, DIP가 적용되어 있으면 **구현체만 교체**하면됩니다.

**❌ DIP 없는 구조:**
```java
@Service
public class RankingService {
    private final RedisTemplate<String, String> redisTemplate;

    public List<ProductRanking> getTopProducts() {
        // Redis에 직접 의존
        Set<ZSetOperations.TypedTuple<String>> result =
            redisTemplate.opsForZSet().reverseRangeWithScores("ranking:daily", 0, 9);

        return result.stream()
            .map(tuple -> new ProductRanking(tuple.getValue(), tuple.getScore()))
            .toList();
    }
}

// 문제점:
// 1. Redis 장애 시 랭킹 조회 불가
// 2. 단위 테스트 시 Redis 필수
// 3. 로컬 캐시 추가 시 전체 수정 필요
```

**✅ DIP 적용 구조:**
```java
// 인터페이스 정의 (Domain Layer)
public interface RankingReader {
    List<ProductRanking> getTopProducts(String rankingType, int limit);
}

// Redis 구현체 (Infrastructure Layer)
@Component
@Primary
public class RedisRankingReader implements RankingReader {
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<ProductRanking> getTopProducts(String rankingType, int limit) {
        String key = "ranking:" + rankingType;
        Set<ZSetOperations.TypedTuple<String>> result =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

        return result.stream()
            .map(tuple -> new ProductRanking(tuple.getValue(), tuple.getScore()))
            .toList();
    }
}

// 2단 캐시 구현체 (Infrastructure Layer)
@Component
@ConditionalOnProperty(name = "ranking.cache.multi-level", havingValue = "true")
public class MultiLevelRankingReader implements RankingReader {
    private final LoadingCache<String, List<ProductRanking>> localCache;
    private final RedisTemplate<String, String> redisTemplate;

    public MultiLevelRankingReader() {
        // Caffeine 로컬 캐시 (1분 TTL)
        this.localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(100)
            .build(key -> loadFromRedis(key));
    }

    @Override
    public List<ProductRanking> getTopProducts(String rankingType, int limit) {
        try {
            // 1차: 로컬 캐시 조회
            return localCache.get(rankingType);
        } catch (Exception e) {
            // 2차: Redis 장애 시 빈 리스트 반환
            log.error("Ranking 조회 실패", e);
            return List.of();
        }
    }

    private List<ProductRanking> loadFromRedis(String rankingType) {
        // Redis 조회 로직
    }
}

// Service Layer (구현체 변경에 영향 없음)
@Service
public class RankingService {
    private final RankingReader rankingReader;  // 인터페이스에 의존

    public List<ProductRanking> getTopProducts() {
        return rankingReader.getTopProducts("daily", 10);
    }
}

// 단위 테스트 (Mock 사용)
@Test
void getTopProducts_Success() {
    // Given
    RankingReader mockReader = mock(RankingReader.class);
    when(mockReader.getTopProducts("daily", 10))
        .thenReturn(List.of(new ProductRanking("P1", 100.0)));

    RankingService service = new RankingService(mockReader);

    // When
    List<ProductRanking> result = service.getTopProducts();

    // Then
    assertThat(result).hasSize(1);
}
```

**실무 사례: Redis 장애 시 2단 캐시 전환**
```
[Before] RedisRankingReader (단일 캐시)
  → Redis 장애 시 랭킹 조회 불가

[After] MultiLevelRankingReader (2단 캐시)
  1차: Caffeine 로컬 캐시 (1분 TTL)
  2차: Redis 캐시 (5분 TTL)
  3차: DB 쿼리 (Fallback)

  → DIP 덕분에 RankingService 코드 변경 없이 구현체만 교체
```

**참고 자료:**
- CQRS (Command Query Responsibility Segregation) 패턴
- Caffeine Cache (로컬 캐시)

**핵심 포인트:**
- 현재 Redis만 사용해도 **DIP 적용 권장**
- 단위 테스트 시 **Mock 처리 용이**
- 요구사항 변경 시 **구현체만 교체**
- Redis 장애 대비 **2단 캐시 전환 가능**

---

### 6. 랭킹 갱신 동기 vs 비동기 (세영님)

**질문:**
> 주문 서비스에서 발생하는 구매 데이터를 바탕으로 일자별 인기 상품 Top-N을 Redis Sorted Set(ZINCRBY)으로만 관리하고, 하루 단위로 TTL로 삭제하는 방식을 적용할 때, 구매 발생 시 바로 갱신하는 동기 업데이트 방식이 일반적인지, 아니면 이벤트 기반으로 비동기 처리하는 방식이 더 안정적인지 궁금합니다.

**제이 코치 답변:**
> **이벤트 기반 비동기 처리가 일반적**입니다.
> 주문 완료 시 `OrderCompletedEvent` 발행하고 별도 핸들러에서 `ZINCRBY` 실행합니다.
> **Redis 장애가 주문 트랜잭션에 영향주지 않도록 격리**하는 게 중요합니다.
> 실시간성이 정말 중요하면 동기로 해도 되지만, 반드시 **try-catch로 감싸서 실패해도 주문은 성공하도록 처리**해야 합니다.

**선택지 비교:**

| 방식 | 장점 | 단점 | 적용 시나리오 |
|------|------|------|---------------|
| **동기 업데이트** | 실시간 반영 | Redis 장애 시 주문 실패 위험 | 실시간성 매우 중요 + Redis 고가용성 보장 |
| **비동기 업데이트 (권장)** | 주문과 랭킹 격리, 안정성 | 약간의 지연 | 일반적인 이커머스 랭킹 |

**❌ 동기 방식 (위험):**
```java
@Transactional
public void createOrder(CreateOrderRequest request) {
    // 1. 주문 생성
    Order order = orderRepository.save(Order.create(request));

    // 2. 결제 처리
    paymentService.processPayment(order);

    // 3. 랭킹 갱신 (Redis 타임아웃 시 주문 실패! ❌)
    redisTemplate.opsForZSet().incrementScore(
        "ranking:daily:20251202",
        order.getProductId(),
        order.getQuantity()
    );

    // 문제: Redis 장애 시 주문 트랜잭션 롤백됨
}
```

**✅ 비동기 방식 (권장):**
```java
// 1. 주문 서비스 (주문만 처리)
@Transactional
public void createOrder(CreateOrderRequest request) {
    // 1. 주문 생성
    Order order = orderRepository.save(Order.create(request));

    // 2. 결제 처리
    paymentService.processPayment(order);

    // 3. 이벤트 발행 (트랜잭션 커밋 후)
    eventPublisher.publishEvent(new OrderCompletedEvent(order));

    // Redis 장애와 무관하게 주문 성공 ✅
}

// 2. 랭킹 갱신 핸들러 (별도 처리)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handleOrderCompleted(OrderCompletedEvent event) {
    try {
        // 랭킹 갱신
        String key = "ranking:daily:" + LocalDate.now().format(DATE_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(
            key,
            event.getProductId(),
            event.getQuantity()
        );
    } catch (Exception e) {
        // 실패해도 주문은 이미 완료됨
        log.error("랭킹 갱신 실패 (주문 정상 처리됨)", e);

        // TODO: 재시도 로직 or 알림
    }
}
```

**실시간성이 중요한 경우 (동기 + 예외 처리):**
```java
@Transactional
public void createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(Order.create(request));
    paymentService.processPayment(order);

    // 동기 처리 but 실패해도 주문은 성공
    try {
        redisTemplate.opsForZSet().incrementScore(
            "ranking:daily:20251202",
            order.getProductId(),
            order.getQuantity()
        );
    } catch (Exception e) {
        // 랭킹 갱신 실패해도 주문은 성공
        log.error("랭킹 갱신 실패 (주문 정상 처리됨)", e);
    }

    // 주문 성공 ✅
}
```

**참고 자료:**
- [Spring Event 공식 문서](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)

**핵심 포인트:**
- **비즈니스 중요도**: 주문 > 랭킹
- 랭킹 갱신 실패가 **주문 실패로 이어지면 안 됨**
- **이벤트 기반 비동기** 처리 권장
- 실시간성 중요하면 **try-catch로 격리**

---

### 7. 일자 변경 시점 처리 (세영님)

**질문:**
> 하루 단위로 랭킹을 날리는 구조라고 할 때 '일자 변경 시점' 처리는 어떤 방식으로 운영하는지 궁금합니다.

**제이 코치 답변:**
> **TTL 기반 자동 만료가 가장 심플**합니다.
> 키 이름에 날짜를 포함하고 25~26시간 TTL을 걸어두면 알아서 사라집니다.
> 자정 정각에 뭔가 하려고 하면 오히려 복잡해져요.

**❌ 스케줄러 방식 (복잡):**
```java
@Scheduled(cron = "0 0 0 * * *")  // 매일 자정
public void switchRanking() {
    String today = LocalDate.now().format(DATE_FORMATTER);
    String yesterday = LocalDate.now().minusDays(1).format(DATE_FORMATTER);

    // 어제 랭킹 삭제
    redisTemplate.delete("ranking:daily:" + yesterday);

    // 문제점:
    // 1. 서버가 3대면 3번 실행됨
    // 2. 서버 시간이 조금씩 다를 수 있음
    // 3. 스케줄러가 멈추면 데이터 누적
}
```

**✅ TTL 방식 (권장):**
```java
// 랭킹 갱신 시 (어느 시점에서든)
public void incrementRanking(String productId, int quantity) {
    LocalDate today = LocalDate.now();
    String key = "ranking:daily:" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    // 1. Score 증가
    redisTemplate.opsForZSet().incrementScore(key, productId, quantity);

    // 2. TTL 설정 (26시간, 여유있게)
    redisTemplate.expire(key, Duration.ofHours(26));
}

// 결과:
// ranking:daily:20251202 (26시간 TTL)
// ranking:daily:20251203 (26시간 TTL)
//
// 12월 2일 데이터는 12월 4일 새벽 2시쯤 자동 삭제 ✅
```

**키 전환 시점:**
```
2025-12-02 23:59:59 → ranking:daily:20251202 에 ZINCRBY
2025-12-03 00:00:00 → ranking:daily:20251203 에 ZINCRBY (새 키 자동 생성)
2025-12-03 01:00:00 → ranking:daily:20251202 자동 만료 (TTL 26시간)
```

**참고 자료:**
- [Redis EXPIRE 공식 문서](https://redis.io/docs/latest/commands/expire/)

**핵심 포인트:**
- 키 이름에 **날짜 포함** (ranking:daily:20251202)
- **25~26시간 TTL** 설정 (여유 시간)
- 스케줄러 불필요, **자동 만료**
- 서버 여러 대여도 문제없음

---

### 8. 일별 랭킹 Redis 단독 관리 (세영님)

**질문:**
> 일별 랭킹처럼 비영구 통계 데이터를 Redis 단독으로 관리하는 것이 실무적으로 문제 없는 방식인지 궁금합니다.

**제이 코치 답변:**
> 일별 랭킹처럼 **휘발되어도 비즈니스 임팩트가 적은 데이터는 Redis 단독 관리가 실무적으로 문제없습니다**.
> Redis 장애 시 데이터 유실 가능성이 있으니 **스냅샷 저장하는 방식**으로 보완하시면 좋겠습니다.

**Redis 단독 관리 가능한 데이터:**
- ✅ 주간/일간 인기 상품 랭킹
- ✅ 실시간 검색어
- ✅ 오늘의 베스트 리뷰
- ✅ 조회수 카운트

**Redis 단독 관리 불가능한 데이터:**
- ❌ 쿠폰 발급 내역 (금전 손실)
- ❌ 결제 대기열 (주문 유실)
- ❌ 포인트 잔액 (금전 손실)
- ❌ 사용자 충전 이력 (법적 증빙)

**✅ 스냅샷 보완 방식:**
```java
// 1. 일간 랭킹 스냅샷 저장 (매일 자정)
@Scheduled(cron = "0 0 0 * * *")
public void saveRankingSnapshot() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    String key = "ranking:daily:" + yesterday.format(DATE_FORMATTER);

    // Redis에서 Top 100 조회
    Set<ZSetOperations.TypedTuple<String>> ranking =
        redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 99);

    // DB에 스냅샷 저장
    List<RankingSnapshot> snapshots = ranking.stream()
        .map(tuple -> RankingSnapshot.of(
            yesterday,
            tuple.getValue(),  // productId
            tuple.getScore().intValue()  // salesCount
        ))
        .toList();

    rankingSnapshotRepository.saveAll(snapshots);
}

// 2. Redis 장애 시 스냅샷 조회
public List<ProductRanking> getTopProducts(String date) {
    try {
        // 1차: Redis 조회
        return getTopProductsFromRedis(date);
    } catch (Exception e) {
        // 2차: DB 스냅샷 조회 (Fallback)
        return rankingSnapshotRepository.findByDate(LocalDate.parse(date));
    }
}
```

**참고 자료:**
- [Redis Persistence 공식 문서](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)

**핵심 포인트:**
- 휘발되어도 괜찮은 데이터만 **Redis 단독**
- 중요한 데이터는 **DB 원장 유지**
- 스냅샷으로 **히스토리 보존**
- Redis 장애 대비 **Fallback 전략**

---

### 9. 카운팅 데이터 동기화 전략 (세영님)

**질문:**
> 조회수/좋아요/검색카운트처럼 카운팅 계열 데이터는 Redis에만 유지하는지(write-back), 아니면 일정 주기로 DB에도 동기화(write-through 또는 배치 반영)하는지 실무에서는 어떤 기준으로 결정하는지 궁금합니다.

**제이 코치 답변:**
> 카운팅 데이터는 **대부분 Write-Back + 주기적 배치 동기화 방식**을 씁니다.
> Write-Through로 매번 DB에 쓰면 DB 부하가 심해지기 때문이에요.
> 다만 **동기화 주기와 유실 허용 범위는 비즈니스 중요도에 따라 결정**합니다.

**선택지 비교:**

| 방식 | 장점 | 단점 | 적용 시나리오 |
|------|------|------|---------------|
| **Write-Through** | 데이터 일관성 보장 | DB 부하 높음 | 결제, 포인트 등 중요 데이터 |
| **Write-Back + 배치** | DB 부하 낮음 | 유실 가능성 | 조회수, 검색 카운트 |
| **Write-Back + 이벤트** | 실시간성 + 부하 분산 | 구현 복잡도 | 좋아요 수 |

**데이터별 전략:**

| 데이터 | 정확도 중요도 | 동기화 전략 | 주기 |
|--------|---------------|-------------|------|
| **조회수** | 낮음 | Write-Back + 배치 | 1시간~1일 |
| **검색 카운트** | 낮음 | Write-Back + 배치 | 1시간~1일 |
| **좋아요 수** | 중간 | Write-Back + 이벤트 | 실시간 (비동기) |
| **재고 수량** | 높음 | Write-Through or Redis 캐시만 | 즉시 |
| **포인트 잔액** | 매우 높음 | Write-Through | 즉시 |

**✅ 구현 예시:**

**1. 조회수 (배치 동기화)**
```java
// 조회수 증가 (Redis만)
public void incrementViewCount(String productId) {
    String key = "product:view:" + productId;
    redisTemplate.opsForValue().increment(key);
}

// 배치 동기화 (1시간마다)
@Scheduled(fixedRate = 3600000)  // 1시간
public void syncViewCountsToDB() {
    Set<String> keys = redisTemplate.keys("product:view:*");

    for (String key : keys) {
        String productId = key.replace("product:view:", "");
        Long viewCount = Long.parseLong(
            redisTemplate.opsForValue().get(key)
        );

        // DB 업데이트
        productRepository.updateViewCount(productId, viewCount);

        // Redis 초기화 (or 차감)
        redisTemplate.delete(key);
    }
}
```

**2. 좋아요 수 (이벤트 기반 비동기)**
```java
// 좋아요 추가 (Redis + 이벤트)
public void addLike(String productId, Long userId) {
    String key = "product:like:" + productId;

    // 1. Redis 즉시 증가
    redisTemplate.opsForValue().increment(key);

    // 2. 이벤트 발행 (비동기 DB 동기화)
    eventPublisher.publishEvent(new ProductLikedEvent(productId, userId));
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handleProductLiked(ProductLikedEvent event) {
    // DB 동기화 (비동기)
    productRepository.incrementLikeCount(event.getProductId());
}
```

**3. 재고 수량 (Redis 캐시 전략)**
```java
// 재고는 DB가 원장, Redis는 캐시
public int getStock(String productId) {
    String key = "product:stock:" + productId;

    // 1. Redis 조회
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return Integer.parseInt(cached);
    }

    // 2. DB 조회 (Cache Miss)
    Product product = productRepository.findById(productId);

    // 3. Redis 캐싱 (짧은 TTL)
    redisTemplate.opsForValue().set(key,
        String.valueOf(product.getStock()),
        Duration.ofSeconds(30)
    );

    return product.getStock();
}

// 재고 차감 (DB 먼저)
@Transactional
public void decreaseStock(String productId, int quantity) {
    // 1. DB 차감
    Product product = productRepository.findById(productId);
    product.decreaseStock(quantity);

    // 2. Redis 캐시 무효화
    redisTemplate.delete("product:stock:" + productId);
}
```

**핵심 포인트:**
- **정확도 낮음** → Write-Back + 배치 (조회수, 검색)
- **정확도 중간** → Write-Back + 이벤트 (좋아요)
- **정확도 높음** → Write-Through or DB 원장 (재고, 포인트)
- **동기화 주기**: 비즈니스 중요도에 따라 결정

---

### 10. Redis 장애 시 데이터 복구 전략 (대원님)

**질문:**
> Redis 장애 시 데이터 복구 전략 (RDB vs AOF):
> 쿠폰 발급이나 결제 대기열 같은 민감한 데이터가 Redis에만 존재하는 순간, Redis가 다운된다면 데이터 유실 위험이 큽니다. 성능을 위해 AOF의 `fsync` 주기를 늦추자니 불안하고, 매번 쓰자니 느립니다. 현업에서는 이러한 고가용성과 성능 사이의 타협점을 보통 어디에 두는지, 혹은 Redis 유실을 대비한 별도의 WAL 시스템(Kafka 등)을 필수적으로 구성하는지 궁금합니다.

**제이 코치 답변:**
> 실무에서는 **AOF `appendfsync everysec` + Master-Replica 구성**이 일반적입니다.
> 데이터 유실 가능성을 감수하되, **Replica failover로 가용성을 확보**하는거죠.
> 쿠폰이나 결제처럼 유실 불가 데이터는 **Kafka를 WAL처럼 사용하여 Redis 복구 가능하게 구성**하거나, **DB를 두고 Redis는 속도 최적화 캐시로만 활용**합니다.

**Redis 영속성 전략 비교:**

| 방식 | 복구 시점 | 성능 | 유실 범위 | 적용 시나리오 |
|------|-----------|------|-----------|---------------|
| **RDB only** | 마지막 스냅샷 | 높음 | 스냅샷 이후 전체 | 캐시 데이터 |
| **AOF appendfsync always** | 최신 | 매우 낮음 | 0 | 금융 데이터 |
| **AOF appendfsync everysec (권장)** | 1초 전 | 높음 | 최대 1초 | 일반 데이터 |
| **AOF + RDB** | 1초 전 | 중간 | 최대 1초 | 혼합 사용 |

**✅ 일반적인 구성 (AOF everysec + Master-Replica):**
```yaml
# redis.conf
appendonly yes
appendfsync everysec  # 1초마다 디스크 동기화

# Master-Replica 구성
replicaof <master-ip> <master-port>

# Sentinel 자동 Failover
sentinel monitor mymaster 127.0.0.1 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

**고가용성 아키텍처:**
```
[Application]
     ↓
[Redis Sentinel]
     ↓
[Master Redis] ← AOF everysec (최대 1초 유실)
     ↓ (Replication)
[Replica 1] [Replica 2]
     ↓
(Master 장애 시 Replica 자동 승격)
```

**유실 불가 데이터 전략:**

**1. Kafka WAL 방식 (권장)**
```java
// 쿠폰 발급 요청 (Kafka에 먼저 기록)
public CouponIssueResult issueCoupon(Long couponId, Long userId) {
    // 1. Kafka에 발급 요청 기록 (WAL)
    kafkaTemplate.send("coupon-issue-wal",
        new CouponIssueRequest(couponId, userId)
    );

    // 2. Consumer가 Redis + DB 처리
    return CouponIssueResult.queued();
}

// Consumer (Kafka → Redis + DB)
@KafkaListener(topics = "coupon-issue-wal")
public void processCouponIssue(CouponIssueRequest request) {
    try {
        // Redis 재고 차감
        Long remain = redisTemplate.opsForValue()
            .decrement("coupon:" + request.getCouponId() + ":remain");

        if (remain < 0) {
            redisTemplate.opsForValue().increment("coupon:" + request.getCouponId() + ":remain");
            return;
        }

        // DB 발급 기록
        couponIssueRepository.save(
            CouponIssue.create(request.getUserId(), request.getCouponId())
        );

        // Redis 발급 기록
        redisTemplate.opsForSet().add(
            "coupon:" + request.getCouponId() + ":issued",
            request.getUserId()
        );

    } catch (Exception e) {
        // 실패 시 Kafka가 재시도
        throw e;
    }
}

// Redis 장애 후 복구 시
public void recoverFromKafka() {
    // Kafka의 오프셋부터 재처리하여 Redis 복구
}
```

**2. DB 원장 + Redis 캐시 방식**
```java
// Redis는 성능 최적화용, DB가 원장
@Transactional
public CouponIssueResult issueCoupon(Long couponId, Long userId) {
    // 1. DB에 먼저 발급 기록 (원장)
    CouponIssue issue = couponIssueRepository.save(
        CouponIssue.create(userId, couponId)
    );

    // 2. Redis 캐시 갱신 (실패해도 괜찮음)
    try {
        redisTemplate.opsForSet().add(
            "coupon:" + couponId + ":issued",
            userId
        );
    } catch (Exception e) {
        log.error("Redis 캐시 갱신 실패 (DB에는 기록됨)", e);
    }

    return CouponIssueResult.success(issue);
}

// 조회 시 (Redis 장애 대비)
public boolean isAlreadyIssued(Long couponId, Long userId) {
    try {
        // 1차: Redis 조회 (빠름)
        return redisTemplate.opsForSet()
            .isMember("coupon:" + couponId + ":issued", userId);
    } catch (Exception e) {
        // 2차: DB 조회 (Fallback)
        return couponIssueRepository.existsByUserIdAndCouponId(userId, couponId);
    }
}
```

**참고 자료:**
- [Redis Sentinel 공식 문서](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)
- [Redis AOF 공식 문서](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/#append-only-file)

**핵심 포인트:**
- **일반 데이터**: AOF everysec + Master-Replica (최대 1초 유실)
- **유실 불가 데이터**: Kafka WAL or DB 원장 + Redis 캐시
- **고가용성**: Sentinel Failover
- **성능 vs 안정성**: 비즈니스 중요도로 결정

---

### 11. Hot Key 문제 해결 (대원님)

**질문:**
> 특정 인기 상품(Hot Key)에 트래픽이 몰려 특정 노드만 죽는 현상은 어떻게 해결하나요?

**제이 코치 답변:**
> **Local Cache와 Redis 2단 캐시 구성**을 추천드립니다.
> 특정 상품에 트래픽이 몰리면 그 키만 **로컬 캐시에 올려서 Redis 호출 자체를 줄이는** 겁니다.
> 또는 **Key 분산으로 하나의 키를 여러 개로 쪼개서 여러 노드에 분배**하는 방법도 있습니다.

**Hot Key 문제 상황:**
```
상품 "P12345" 초특가 진행
    ↓
1만 req/sec → Redis Node 3 (P12345 담당)
    ↓
CPU 100%, 메모리 폭증
    ↓
Node 3 장애 → 전체 클러스터 영향
```

**✅ 해결 방법 1: Local Cache + Redis 2단 캐시 (권장)**
```java
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Product> localCache() {
        // Caffeine 로컬 캐시 (1분 TTL, 최대 1만개)
        return Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10000)
            .recordStats()  // 히트율 모니터링
            .build();
    }
}

@Service
public class ProductService {
    private final Cache<String, Product> localCache;
    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    public Product getProduct(String productId) {
        // 1차: 로컬 캐시 조회 (가장 빠름, Redis 호출 없음)
        Product cached = localCache.getIfPresent(productId);
        if (cached != null) {
            return cached;
        }

        // 2차: Redis 조회
        String redisData = redisTemplate.opsForValue().get("product:" + productId);
        if (redisData != null) {
            Product product = objectMapper.readValue(redisData, Product.class);
            localCache.put(productId, product);  // 로컬 캐시 갱신
            return product;
        }

        // 3차: DB 조회 (Cache Miss)
        Product product = productRepository.findById(productId);

        // Redis + 로컬 캐시 갱신
        redisTemplate.opsForValue().set("product:" + productId,
            objectMapper.writeValueAsString(product),
            Duration.ofMinutes(5)
        );
        localCache.put(productId, product);

        return product;
    }
}
```

**효과:**
```
[Before] 1만 req/sec → Redis
[After]  9천 req/sec → 로컬 캐시 (99% hit)
         1천 req/sec → Redis (1% miss)

Redis 부하 90% 감소 ✅
```

**✅ 해결 방법 2: Key 분산 (Sharding)**
```java
// Hot Key를 여러 개로 쪼개서 여러 노드에 분배
public Product getProduct(String productId) {
    // 랜덤하게 샤드 선택 (0~9)
    int shardId = ThreadLocalRandom.current().nextInt(10);
    String key = "product:" + productId + ":shard:" + shardId;

    // 여러 노드에 분산 저장
    String data = redisTemplate.opsForValue().get(key);

    if (data == null) {
        // DB 조회
        Product product = productRepository.findById(productId);

        // 모든 샤드에 복제
        for (int i = 0; i < 10; i++) {
            redisTemplate.opsForValue().set(
                "product:" + productId + ":shard:" + i,
                objectMapper.writeValueAsString(product),
                Duration.ofMinutes(5)
            );
        }

        return product;
    }

    return objectMapper.readValue(data, Product.class);
}
```

**✅ 해결 방법 3: Hot Key 동적 감지**
```java
@Component
public class HotKeyDetector {
    private final ConcurrentHashMap<String, AtomicLong> keyAccessCount = new ConcurrentHashMap<>();
    private final Cache<String, Product> hotKeyCache;

    @Scheduled(fixedRate = 10000)  // 10초마다 검사
    public void detectHotKeys() {
        keyAccessCount.forEach((key, count) -> {
            if (count.get() > 1000) {  // 10초간 1000회 이상 조회
                log.warn("Hot Key 감지: {}", key);

                // 로컬 캐시 활성화
                Product product = getProductFromRedis(key);
                hotKeyCache.put(key, product);
            }
        });

        keyAccessCount.clear();  // 카운터 초기화
    }

    public Product getProduct(String productId) {
        // 접근 카운트 증가
        keyAccessCount.computeIfAbsent(productId, k -> new AtomicLong())
            .incrementAndGet();

        // Hot Key 감지 시 로컬 캐시 사용
        Product cached = hotKeyCache.getIfPresent(productId);
        if (cached != null) {
            return cached;
        }

        // 일반 Redis 조회
        return getProductFromRedis(productId);
    }
}
```

**참고 자료:**
- Caffeine Cache (로컬 캐시 라이브러리)
- Redis Hot Key 감지 및 대응 전략

**핵심 포인트:**
- **로컬 캐시 + Redis 2단 캐시** (가장 효과적)
- **Key 분산**: 하나의 키를 여러 샤드로
- **Hot Key 감지**: 동적으로 로컬 캐시 활성화
- **Redis 호출 90% 감소** 가능

---

### 12. 메모리 관리 전략 (대원님)

**질문:**
> 메모리가 꽉 찼을 때 중요한 데이터(대기열 등)가 날아가는 것은 어떻게 방지하나요?

**제이 코치 답변:**
> **maxmemory-policy 설정과 Redis 인스턴스 분리**로 해결합니다.
> 캐시용 Redis와 핵심 데이터용 Redis를 분리하고, 캐시용은 **allkeys-lru**로 설정해서 메모리 부족하면 오래된 캐시부터 삭제하고, 핵심 데이터용은 **noeviction**으로 설정해서 메모리 꽉 차면 삭제 대신 쓰기 에러가 나도록 합니다.

**Redis Eviction Policy 비교:**

| Policy | 동작 | 적용 대상 | 사용 시나리오 |
|--------|------|-----------|---------------|
| **noeviction** | 메모리 꽉 차면 쓰기 에러 | 모든 키 | 핵심 데이터 (대기열, 세션) |
| **allkeys-lru** | 가장 오래 사용 안 한 키 삭제 | 모든 키 | 캐시 데이터 |
| **volatile-lru** | TTL 있는 키 중 LRU 삭제 | TTL 키만 | 혼합 사용 |
| **allkeys-lfu** | 가장 적게 사용된 키 삭제 | 모든 키 | 캐시 (접근 빈도 기준) |

**❌ 잘못된 구성 (단일 Redis):**
```yaml
# redis.conf (혼용 - 위험!)
maxmemory 2gb
maxmemory-policy allkeys-lru  # 모든 키 삭제 가능

# 문제:
# - 캐시 데이터로 메모리 꽉 참
# - 중요한 대기열 데이터도 삭제됨 ❌
```

**✅ 올바른 구성 (Redis 인스턴스 분리):**

**1. 캐시용 Redis (Port 6379)**
```yaml
# redis-cache.conf
maxmemory 4gb
maxmemory-policy allkeys-lru  # 오래된 캐시 자동 삭제

# 용도:
# - 상품 정보 캐시
# - 랭킹 데이터
# - 집계 데이터
```

**2. 핵심 데이터용 Redis (Port 6380)**
```yaml
# redis-data.conf
maxmemory 2gb
maxmemory-policy noeviction  # 메모리 꽉 차면 쓰기 에러

# 용도:
# - 쿠폰 대기열
# - 세션 데이터
# - 분산 락
```

**Application 설정 (Spring Boot):**
```yaml
spring:
  redis:
    cache:  # 캐시용
      host: localhost
      port: 6379
    data:   # 핵심 데이터용
      host: localhost
      port: 6380
```

```java
@Configuration
public class RedisConfig {

    @Bean(name = "cacheRedisTemplate")
    public RedisTemplate<String, String> cacheRedisTemplate(
        @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory factory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }

    @Bean(name = "dataRedisTemplate")
    public RedisTemplate<String, String> dataRedisTemplate(
        @Qualifier("dataRedisConnectionFactory") RedisConnectionFactory factory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }
}

@Service
public class ProductService {
    @Autowired
    @Qualifier("cacheRedisTemplate")
    private RedisTemplate<String, String> cacheRedis;  // 캐시용

    @Autowired
    @Qualifier("dataRedisTemplate")
    private RedisTemplate<String, String> dataRedis;  // 대기열용
}
```

**✅ TTL 전략 (volatile-lru 사용 시)**
```java
// 캐시 데이터: TTL 설정 (자동 삭제 대상)
redisTemplate.opsForValue().set("product:" + productId, data,
    Duration.ofMinutes(5)  // TTL 5분
);

// 중요 데이터: TTL 없음 (삭제 방지)
redisTemplate.opsForSet().add("coupon:queue", userId);
// TTL 설정 안 함 → volatile-lru 정책에서 삭제 안 됨
```

**✅ 메모리 모니터링 및 알림**
```java
@Component
public class RedisMemoryMonitor {

    @Scheduled(fixedRate = 60000)  // 1분마다
    public void checkMemory() {
        Properties info = redisConnection.info("memory");
        long usedMemory = Long.parseLong(info.getProperty("used_memory"));
        long maxMemory = Long.parseLong(info.getProperty("maxmemory"));

        double usage = (double) usedMemory / maxMemory * 100;

        if (usage > 80) {
            log.warn("Redis 메모리 사용률: {}%", usage);
            // Slack 알림 전송
            slackService.sendAlert("Redis 메모리 사용률 80% 초과");
        }

        if (usage > 90) {
            log.error("Redis 메모리 위험: {}%", usage);
            // 긴급 알림 + Auto Scale-Up 트리거
        }
    }
}
```

**참고 자료:**
- [Redis Eviction 공식 문서](https://redis.io/docs/latest/develop/reference/eviction/)
- [Redis Memory Optimization](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/memory-optimization/)
- [DataDog Redis 모니터링 가이드](https://www.datadoghq.com/blog/how-to-monitor-redis-performance-metrics/)

**핵심 포인트:**
- **Redis 인스턴스 분리** (캐시용 / 핵심 데이터용)
- 캐시용: **allkeys-lru** (자동 삭제)
- 핵심 데이터용: **noeviction** (쓰기 에러)
- **메모리 70~80% 알림** 설정
- TTL 전략으로 중요 데이터 보호

---

### 13. Redis 모니터링 지표 (모두)

**제이 코치 답변:**
> 구현이 끝이 아니기 때문에 어떤 지표들을 Redis 모니터링을 하느냐 사실 이런 것들이 되게 중요하거든요.
> 성과 지표는 어떤 걸 보고 메모리 매트릭을 어떤 걸 보느냐 이런 것들을 계산하는 것들을 조금 보시면 **지금 한번 같이 나가 볼까요?**

**주요 Redis 모니터링 지표:**

#### 1. 성과 지표 (Performance Metrics)

| 지표 | 설명 | 확인 명령어 | 정상 범위 |
|------|------|-------------|-----------|
| **Throughput** | 초당 처리 명령 수 | `INFO stats` → `instantaneous_ops_per_sec` | 수천~수만 |
| **Latency** | 명령 응답 시간 | `SLOWLOG GET 10` | < 1ms |
| **Hit Rate** | 캐시 히트율 | `keyspace_hits / (keyspace_hits + keyspace_misses)` | > 80% |

**Hit Rate 계산:**
```bash
# Redis CLI
redis-cli INFO stats | grep keyspace

# 결과:
keyspace_hits:8500
keyspace_misses:1500

# Hit Rate = 8500 / (8500 + 1500) = 85%
```

```java
// Java 코드로 계산
public double getHitRate() {
    Properties stats = redisConnection.info("stats");
    long hits = Long.parseLong(stats.getProperty("keyspace_hits"));
    long misses = Long.parseLong(stats.getProperty("keyspace_misses"));

    return (double) hits / (hits + misses) * 100;
}
```

#### 2. 메모리 지표 (Memory Metrics)

| 지표 | 설명 | 확인 명령어 | 주의 기준 |
|------|------|-------------|-----------|
| **used_memory** | 사용 중인 메모리 | `INFO memory` → `used_memory_human` | > 80% |
| **mem_fragmentation_ratio** | 메모리 조각화 비율 | `INFO memory` → `mem_fragmentation_ratio` | > 1.5 |
| **evicted_keys** | 제거된 키 수 | `INFO stats` → `evicted_keys` | 증가 추세 주의 |

**메모리 사용률 모니터링:**
```java
@Scheduled(fixedRate = 60000)
public void monitorMemory() {
    Properties memInfo = redisConnection.info("memory");

    long usedMemory = Long.parseLong(memInfo.getProperty("used_memory"));
    long maxMemory = Long.parseLong(memInfo.getProperty("maxmemory"));
    double fragmentation = Double.parseDouble(
        memInfo.getProperty("mem_fragmentation_ratio")
    );

    double usage = (double) usedMemory / maxMemory * 100;

    log.info("Redis 메모리 사용률: {}%, 조각화: {}", usage, fragmentation);

    if (usage > 80) {
        alertService.sendWarning("Redis 메모리 사용률 80% 초과");
    }

    if (fragmentation > 1.5) {
        log.warn("메모리 조각화 높음: {}", fragmentation);
    }
}
```

#### 3. 기본 활용 지표

| 지표 | 설명 | 확인 명령어 | 의미 |
|------|------|-------------|------|
| **connected_clients** | 연결된 클라이언트 수 | `INFO clients` → `connected_clients` | 급증 시 연결 누수 |
| **blocked_clients** | 대기 중인 클라이언트 | `INFO clients` → `blocked_clients` | > 0 시 성능 저하 |
| **total_connections_received** | 총 연결 수 | `INFO stats` → `total_connections_received` | 증가 패턴 확인 |

#### 4. 에러 지표

| 지표 | 설명 | 확인 명령어 | 조치 |
|------|------|-------------|------|
| **rejected_connections** | 거부된 연결 수 | `INFO stats` → `rejected_connections` | maxclients 증가 |
| **keyspace_misses** | 캐시 미스 수 | `INFO stats` → `keyspace_misses` | 캐시 전략 재검토 |

**DataDog 대시보드 예시:**
```yaml
# DataDog Redis 모니터링 설정
monitors:
  - name: "Redis 메모리 사용률 80% 초과"
    type: metric alert
    query: "avg(last_5m):avg:redis.mem.used{*} / avg:redis.mem.maxmemory{*} > 0.8"
    message: "Redis 메모리 사용률이 80%를 초과했습니다. Scale-Up 검토 필요."

  - name: "Redis Hit Rate 70% 미만"
    type: metric alert
    query: "avg(last_10m):(redis.stats.keyspace_hits{*} / (redis.stats.keyspace_hits{*} + redis.stats.keyspace_misses{*})) < 0.7"
    message: "Redis Hit Rate가 70% 미만입니다. 캐시 전략 재검토 필요."

  - name: "Redis Latency 10ms 초과"
    type: metric alert
    query: "avg(last_5m):redis.info.latency_ms{*} > 10"
    message: "Redis 응답 지연이 10ms를 초과했습니다."
```

**참고 자료:**
- [DataDog - Redis 모니터링 가이드](https://www.datadoghq.com/blog/how-to-monitor-redis-performance-metrics/)

**핵심 포인트:**
- **Hit Rate > 80%** 유지
- **메모리 사용률 < 80%** 유지
- **Latency < 1ms** 유지
- **mem_fragmentation_ratio < 1.5** 유지
- 지표 모니터링 및 알림 필수

---

## 🚀 실무 관점 정리

### AWS 인프라 학습의 중요성

**제이 코치 발언:**
> "여러분이 가고 싶은 회사는 다 AWS 쓸걸요.
> **GCP나 AWS나 사실 기본 개념들은 다 비슷비슷**하니까 보시면 좋지 않을까 싶기는 해요.
> 일반 로컬에서 개발할 때랑 **프로드로 개발할 때랑은 조금 많이 틀리다 보니까** 이런 부분들이 조금 갭 차이가 조금 나가지고 힘들어하더라고요."

**권장 학습 자료:**
- AWS 관리 기술 서적
- 실제 인프라 환경 이해 (Dev vs Stage vs Prod)
- 인프라 설계 경험

**Dev vs Prod 환경 차이:**
```
[Dev 환경]
- 단일 인스턴스
- RDB, AOF 미사용
- 모니터링 간소화

[Prod 환경]
- Master-Replica 구성
- Sentinel Failover
- AOF everysec + RDB 스냅샷
- 철저한 모니터링 (DataDog, Grafana)
- 알림 시스템
```

---

## ✅ 적용 체크리스트

### Redis 설계
- [ ] 동점 처리 방식 결정 (사전순 or 타임스탬프)
- [ ] TransactionalEventListener 활용 (DB 먼저, Redis 나중)
- [ ] 분산락 최소화, INCR/DECR 활용
- [ ] 선착순 판정과 발급 처리 분리
- [ ] DIP 적용 (인터페이스 분리)

### 성능 최적화
- [ ] 랭킹 갱신 비동기 처리
- [ ] TTL 기반 일자 변경
- [ ] 스냅샷으로 히스토리 보존
- [ ] Write-Back + 배치 동기화
- [ ] Local Cache + Redis 2단 캐시

### 안정성
- [ ] AOF everysec + Master-Replica
- [ ] Kafka WAL 구성 (유실 불가 데이터)
- [ ] Hot Key 감지 및 대응
- [ ] Redis 인스턴스 분리 (캐시 / 핵심 데이터)
- [ ] 메모리 모니터링 및 알림

### 모니터링
- [ ] Hit Rate > 80% 유지
- [ ] 메모리 사용률 < 80% 유지
- [ ] Latency < 1ms 유지
- [ ] DataDog 대시보드 구성
- [ ] Slack 알림 연동

---

## 🔗 전체 참고 자료 링크

### Redis 공식 문서
- [Redis ZADD (동점 처리)](https://redis.io/docs/latest/commands/zadd/)
- [Redis EXPIRE (TTL)](https://redis.io/docs/latest/commands/expire/)
- [Redis Persistence (RDB, AOF)](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
- [Redis Eviction Policy](https://redis.io/docs/latest/develop/reference/eviction/)
- [Redis Memory Optimization](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/memory-optimization/)
- [Redis Sentinel](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)

### Spring 공식 문서
- [TransactionalEventListener](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring Event](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)

### 기술 블로그
- [우아한형제들 - Redis 활용 사례](https://techblog.woowahan.com/2601/)
- [우아한형제들 - 이벤트 기반 아키텍처](https://techblog.woowahan.com/7835/)
- [컬리 - 분산 락 활용 (Redisson)](https://helloworld.kurly.com/blog/distributed-redisson-lock/)

### 모니터링
- [DataDog - Redis 모니터링 가이드](https://www.datadoghq.com/blog/how-to-monitor-redis-performance-metrics/)

---

**마지막 업데이트:** 2025.12.02
**문서 작성자:** 박지수 (제이 코치님 멘토링 기반)
