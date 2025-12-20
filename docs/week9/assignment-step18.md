# Step 18: Kafka를 활용한 비즈니스 프로세스 개선

> **목표**: Kafka의 특징(파티셔닝, 병렬 처리)을 활용하여 대용량 트래픽 프로세스를 개선한다.

---

## 📋 과제 개요

### 학습 목표
1. Kafka 파티션 기반 병렬 처리 전략 설계
2. 메시지 키를 활용한 순서 보장 전략 수립
3. 선착순 쿠폰 발급 또는 콘서트 대기열 처리를 Kafka로 개선
4. 설계 문서 및 시퀀스 다이어그램 작성
5. 성능 개선 효과 측정 및 분석

### 예상 소요 시간
- **최소 (기본 과제)**: 5-6시간
- **권장 (설계 + 구현)**: 8-10시간

---

## 🎯 과제 요구사항

### 필수 요구사항 (Pass 조건)

#### 1. 비즈니스 시나리오 선택

**선택지**
- A. 선착순 쿠폰 발급 (이커머스)
- B. 콘서트 대기열 토큰 활성화 (콘서트)

**선택 기준**
- **선착순 쿠폰**: 병렬 처리 + 순서 보장의 하이브리드
- **대기열**: 전체 순서 보장 + 속도 제어

#### 2. Kafka 기반 설계 문서 작성 (30%)

**요구사항**
- 기존 방식(Redis/Application Event)의 한계 분석
- Kafka를 활용한 개선 방안 설계
- 파티션 전략 및 Consumer 구성 명시
- 예상 개선 효과

**산출물**
```
docs/week9/
└── {시나리오명}-kafka-design.md
    ├── 1. 기존 방식의 한계
    ├── 2. Kafka 기반 개선 설계
    │   ├── Topic 설계
    │   ├── 파티션 전략
    │   ├── Consumer Group 구성
    │   └── 메시지 키 전략
    ├── 3. 시퀀스 다이어그램 (Mermaid)
    ├── 4. Kafka 구성도
    └── 5. 예상 개선 효과
```

**평가 기준**
- 기존 방식의 문제점을 정확히 파악
- Kafka의 특징을 활용한 개선 방안 제시
- 파티션/Consumer 전략이 비즈니스 요구사항과 부합
- 다이어그램으로 명확히 시각화

#### 3. 시퀀스 다이어그램 작성 (20%)

**요구사항**
- Kafka 메시지 흐름을 Mermaid 다이어그램으로 시각화
- Producer → Kafka → Consumer 전체 흐름 표현
- 파티션 분배 및 순서 보장 표현

**예시 (선착순 쿠폰)**
```mermaid
sequenceDiagram
    participant U as User
    participant API as API Server
    participant K as Kafka (Partition 0,1,2)
    participant C1 as Consumer 1
    participant C2 as Consumer 2
    participant C3 as Consumer 3
    participant DB as Database

    U->>API: 쿠폰 발급 요청 (쿠폰 A)
    API->>K: Publish (Key: coupon-A, Partition 0)

    K->>C1: Consume (Partition 0)
    C1->>DB: 쿠폰 발급 처리
    C1->>K: ACK

    Note over C1,C2,C3: 각 Consumer는 독립적으로<br/>할당된 파티션 처리
```

#### 4. 코드 구현 (30%)

**요구사항**
- 설계에 따라 Producer/Consumer 구현
- 파티션 전략 적용 (메시지 키 지정)
- DLQ 처리 (Dead Letter Queue)
- 테스트 코드

**체크리스트**
- [ ] Topic 생성 (CLI 또는 코드)
- [ ] Producer 구현 (메시지 키 지정)
- [ ] Consumer 구현 (파티션별 처리)
- [ ] DLQ Consumer 구현
- [ ] 통합 테스트
- [ ] 동시성 테스트 (선택)

#### 5. 개선 효과 정리 (20%)

**요구사항**
- 기존 방식 대비 개선 효과 측정 및 정리
- 처리량, Lag, 에러율 등의 지표 포함
- 장단점 비교

**측정 지표**
- TPS (Transactions Per Second)
- Latency (P50, P95, P99)
- Consumer Lag
- 에러율

---

## 📝 구현 가이드

### 시나리오 A: 선착순 쿠폰 발급

#### 기존 방식의 한계

**Redis 기반 동시성 제어**
```java
// Redis Lua Script
public Long issueCoupon(String couponId, String userId) {
    String script =
        "local count = redis.call('incr', KEYS[1]) " +
        "if count <= tonumber(ARGV[1]) then " +
        "    return count " +
        "else " +
        "    redis.call('decr', KEYS[1]) " +
        "    return 0 " +
        "end";

    return redisTemplate.execute(
        new DefaultRedisScript<>(script, Long.class),
        List.of("coupon:" + couponId),
        String.valueOf(maxQuantity)
    );
}
```

**문제점**
1. **단일 쿠폰 처리 병목**: 모든 요청이 하나의 Redis 키에 집중
2. **확장성 제한**: Redis 단일 노드의 처리량 한계
3. **장애 복구 어려움**: Redis 장애 시 재처리 불가
4. **모니터링 부족**: 처리 상태 추적 어려움

#### Kafka 기반 개선 설계

**핵심 아이디어**
- **메시지 키 = 쿠폰 ID**: 같은 쿠폰은 같은 파티션 (순서 보장)
- **파티션 수 = 동시 발급 쿠폰 수**: 다른 쿠폰은 병렬 처리
- **Consumer 수 = 파티션 수**: 최대 병렬 처리

**Topic 설계**
```
Topic: "coupon-issuance"
Partitions: 12
Replication Factor: 3 (프로덕션)
Retention: 7 days
```

**파티셔닝 전략**
```
Key: couponId (예: "coupon-77")
Hash: hash("coupon-77") % 12 = Partition 2

→ 모든 "coupon-77" 요청은 Partition 2로
→ Consumer 1이 순차 처리
```

**Consumer Group 구성**
```
Consumer Group: "coupon-processor"
├── Consumer 1 → Partition 0, 1, 2, 3
├── Consumer 2 → Partition 4, 5, 6, 7
└── Consumer 3 → Partition 8, 9, 10, 11
```

#### 구현

**1. 메시지 DTO**
```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponIssuanceMessage {
    private String couponId;
    private String userId;
    private LocalDateTime requestedAt;
}
```

**2. Producer**
```java
@Component
@RequiredArgsConstructor
public class CouponIssuanceProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void requestCouponIssuance(String couponId, String userId) {
        CouponIssuanceMessage message = CouponIssuanceMessage.builder()
                .couponId(couponId)
                .userId(userId)
                .requestedAt(LocalDateTime.now())
                .build();

        // Key: couponId (같은 쿠폰은 같은 파티션)
        kafkaTemplate.send("coupon-issuance", couponId, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Coupon issuance request published: couponId={}, userId={}, partition={}",
                                couponId, userId, result.getRecordMetadata().partition());
                    } else {
                        log.error("Failed to publish coupon issuance request", ex);
                    }
                });
    }
}
```

**3. Consumer**
```java
@Component
@RequiredArgsConstructor
public class CouponIssuanceConsumer {

    private final CouponService couponService;

    @KafkaListener(
            topics = "coupon-issuance",
            groupId = "coupon-processor",
            concurrency = "3"  // 3개 Consumer
    )
    public void handleCouponIssuance(
            CouponIssuanceMessage message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment ack
    ) {
        log.info("Processing coupon issuance: couponId={}, userId={}, partition={}",
                message.getCouponId(), message.getUserId(), partition);

        try {
            // 쿠폰 발급 처리 (DB 트랜잭션)
            couponService.issueCoupon(message.getCouponId(), message.getUserId());

            // 처리 성공 시 ACK
            ack.acknowledge();

            log.info("Coupon issued successfully: couponId={}, userId={}",
                    message.getCouponId(), message.getUserId());
        } catch (CouponSoldOutException e) {
            log.warn("Coupon sold out: couponId={}", message.getCouponId());
            ack.acknowledge();  // 재처리 불필요
        } catch (Exception e) {
            log.error("Failed to issue coupon: couponId={}, userId={}",
                    message.getCouponId(), message.getUserId(), e);
            // ACK 하지 않음 → 재처리
        }
    }
}
```

**4. API Controller**
```java
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponIssuanceProducer couponIssuanceProducer;

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<CouponIssuanceResponse>> issueCoupon(
            @PathVariable String couponId,
            @RequestBody CouponIssuanceRequest request
    ) {
        // Kafka에 메시지만 발행
        couponIssuanceProducer.requestCouponIssuance(couponId, request.getUserId());

        // 즉시 응답 (비동기 처리)
        return ResponseEntity.ok(ApiResponse.success(
                CouponIssuanceResponse.builder()
                        .couponId(couponId)
                        .userId(request.getUserId())
                        .status("REQUESTED")
                        .message("쿠폰 발급이 요청되었습니다. 잠시 후 확인해주세요.")
                        .build()
        ));
    }

    @GetMapping("/my-coupons")
    public ResponseEntity<ApiResponse<List<UserCoupon>>> getMyCoupons(
            @RequestParam String userId
    ) {
        // 발급된 쿠폰 조회
        List<UserCoupon> coupons = couponService.getUserCoupons(userId);
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }
}
```

#### 효과

**처리량 향상**
```
[Before - Redis]
- 쿠폰 A: 1000 TPS (Redis 단일 키 한계)
- 쿠폰 B: 1000 TPS
- 총 처리량: 2000 TPS

[After - Kafka]
- 쿠폰 A: 1000 TPS (Partition 0 → Consumer 1)
- 쿠폰 B: 1000 TPS (Partition 1 → Consumer 2)
- 쿠폰 C: 1000 TPS (Partition 2 → Consumer 3)
- ...
- 총 처리량: 12000 TPS (12 Partitions)
```

**장점**
- ✅ 동일 쿠폰: 순차 처리 (동시성 제어)
- ✅ 다른 쿠폰: 병렬 처리 (처리량 향상)
- ✅ Consumer 추가로 확장 가능
- ✅ 메시지 영구 저장 (재처리 가능)
- ✅ Lag 모니터링 가능

**단점**
- ⚠️ 즉시 응답 불가 (비동기 처리)
- ⚠️ 인프라 복잡도 증가 (Kafka 관리)

---

### 시나리오 B: 콘서트 대기열 토큰 활성화

#### 기존 방식의 한계

**Redis Sorted Set 기반**
```java
public void addToQueue(String token) {
    double score = System.currentTimeMillis();
    redisTemplate.opsForZSet().add("waiting-queue", token, score);
}

@Scheduled(fixedDelay = 1000)
public void activateTokens() {
    Set<String> tokens = redisTemplate.opsForZSet()
            .range("waiting-queue", 0, 99);  // 100개씩 활성화

    tokens.forEach(token -> {
        activateToken(token);
        redisTemplate.opsForZSet().remove("waiting-queue", token);
    });
}
```

**문제점**
1. **Scheduler 단일 노드**: 스케일 아웃 어려움
2. **재처리 어려움**: 활성화 실패 시 복구 불가
3. **순서 보장 불완전**: Scheduler 실행 시점에 따라 순서 변경 가능
4. **모니터링 부족**: 대기열 상태 추적 어려움

#### Kafka 기반 개선 설계

**핵심 아이디어**
- **파티션 1개**: 전체 순서 보장
- **Consumer 1개**: 순차 처리
- **속도 제어**: Consumer에서 Rate Limiting

**Topic 설계**
```
Topic: "waiting-token"
Partitions: 1 (순서 보장)
Replication Factor: 3
Retention: 1 day
```

**Consumer 구성**
```
Consumer Group: "token-activator"
└── Consumer 1 → Partition 0 (순차 처리)
```

#### 구현

**1. 메시지 DTO**
```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingTokenMessage {
    private String tokenId;
    private String userId;
    private String concertId;
    private LocalDateTime enqueuedAt;
}
```

**2. Producer**
```java
@Component
@RequiredArgsConstructor
public class WaitingQueueProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void enqueue(String tokenId, String userId, String concertId) {
        WaitingTokenMessage message = WaitingTokenMessage.builder()
                .tokenId(tokenId)
                .userId(userId)
                .concertId(concertId)
                .enqueuedAt(LocalDateTime.now())
                .build();

        // Key: null (순서대로 발행)
        kafkaTemplate.send("waiting-token", message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Token enqueued: tokenId={}, offset={}",
                                tokenId, result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to enqueue token", ex);
                    }
                });
    }
}
```

**3. Consumer (Rate Limiting)**
```java
@Component
@RequiredArgsConstructor
public class WaitingTokenConsumer {

    private final WaitingQueueService waitingQueueService;
    private final RateLimiter rateLimiter = RateLimiter.create(100.0);  // 초당 100개

    @KafkaListener(
            topics = "waiting-token",
            groupId = "token-activator"
    )
    public void handleTokenActivation(
            WaitingTokenMessage message,
            Acknowledgment ack
    ) {
        // Rate Limiting (초당 100개 제한)
        rateLimiter.acquire();

        log.info("Activating token: tokenId={}", message.getTokenId());

        try {
            // 토큰 활성화
            waitingQueueService.activateToken(message.getTokenId());

            // 처리 성공 시 ACK
            ack.acknowledge();

            log.info("Token activated: tokenId={}", message.getTokenId());
        } catch (Exception e) {
            log.error("Failed to activate token: tokenId={}", message.getTokenId(), e);
            // ACK 하지 않음 → 재처리
        }
    }
}
```

#### 효과

**순서 보장**
```
[Before - Redis Scheduler]
- Scheduler 실행 간격: 1초
- 동시 활성화: 100개
- 순서 보장: 1초 단위로만 보장

[After - Kafka]
- 메시지 순서: Offset 기반 (완벽 보장)
- 처리 속도: 초당 100개 (Rate Limiter)
- 순서 보장: 완벽
```

**장점**
- ✅ 완벽한 순서 보장 (Offset 기반)
- ✅ 재처리 가능 (메시지 저장)
- ✅ Lag 모니터링 (대기 인원 파악)
- ✅ Consumer 재시작 시에도 이어서 처리

**단점**
- ⚠️ 처리량 제한 (파티션 1개, Consumer 1개)
- ⚠️ 확장 어려움 (파티션 추가 시 순서 보장 불가)

---

## 🧪 테스트

### 통합 테스트

**선착순 쿠폰**
```java
@SpringBootTest
@Testcontainers
class CouponIssuanceKafkaTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.3")
    );

    @Test
    void 동시_100명_쿠폰_발급_요청() throws InterruptedException {
        // Given
        String couponId = "coupon-100";
        int maxQuantity = 10;
        int requestCount = 100;

        CountDownLatch latch = new CountDownLatch(requestCount);
        ExecutorService executor = Executors.newFixedThreadPool(100);

        // When
        for (int i = 0; i < requestCount; i++) {
            String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    couponIssuanceProducer.requestCouponIssuance(couponId, userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        Thread.sleep(5000);  // Consumer 처리 대기

        // Then
        List<UserCoupon> issuedCoupons = couponRepository.findByCouponId(couponId);
        assertThat(issuedCoupons).hasSize(maxQuantity);  // 정확히 10개만 발급
    }
}
```

---

## 📊 평가 기준

### Pass 조건

| 항목 | 배점 | 기준 |
|------|------|------|
| **설계 문서** | 30% | 파티션 전략, Consumer 구성 명확히 설명 |
| **시퀀스 다이어그램** | 20% | Kafka 메시지 흐름 시각화 |
| **코드 구현** | 30% | 설계대로 동작하는 코드 |
| **개선 효과** | 20% | 기존 방식 대비 장점 설명 |

### 도전 과제 (추가 가산점)

| 항목 | 가산점 | 기준 |
|------|--------|------|
| **DLQ 자동화** | +10% | DB 저장 + 재처리 로직 |
| **성능 측정** | +10% | TPS, Latency, Lag 측정 |
| **모니터링** | +10% | Grafana 대시보드 |
| **동시성 테스트** | +10% | 100명 동시 요청 테스트 |

---

## 💡 팁과 주의사항

### 설계 시 고려사항

#### 1. 파티션 수 결정
- **시작**: 3개 (보수적)
- **확장**: Lag 발생 시 증가
- **고려**: Consumer 수 ≤ Partition 수

#### 2. 메시지 키 전략
- **쿠폰 발급**: `couponId` (같은 쿠폰은 순차 처리)
- **대기열**: `null` (전체 순서 보장)
- **주문**: `userId` (같은 유저는 순차 처리)

#### 3. Consumer Concurrency
```yaml
spring:
  kafka:
    listener:
      concurrency: 3  # 파티션 수와 동일 또는 작게
```

### 성능 측정 방법

**1. Consumer Lag 모니터링**
```bash
docker exec -it kafka kafka-consumer-groups --describe \
  --group coupon-processor \
  --bootstrap-server localhost:9092

# LAG: 처리하지 못한 메시지 수
```

**2. 처리량 측정**
```java
@Component
public class MetricsCollector {

    private final AtomicLong processedCount = new AtomicLong(0);

    @Scheduled(fixedDelay = 1000)
    public void reportMetrics() {
        long count = processedCount.getAndSet(0);
        log.info("Processed {} messages in 1 second (TPS: {})", count, count);
    }

    public void incrementProcessedCount() {
        processedCount.incrementAndGet();
    }
}
```

---

## 📚 참고 자료

### 필수 읽기
- [kafka-basics.md](./kafka-basics.md)
- [kafka-use-cases.md](./kafka-use-cases.md)
- [kafka-best-practices.md](./kafka-best-practices.md)

### 실무 사례
- 토스 외화 이체 시스템 (비동기 처리)
- 쿠팡 물류 시스템 (CDC + Kafka)

---

## ✅ 제출 체크리스트

### 문서
- [ ] 설계 문서 (`{시나리오}-kafka-design.md`)
- [ ] 시퀀스 다이어그램 (Mermaid)
- [ ] Kafka 구성도
- [ ] 개선 효과 정리

### 코드
- [ ] Producer 구현
- [ ] Consumer 구현
- [ ] DLQ Consumer 구현 (선택)
- [ ] 테스트 코드

### 테스트
- [ ] 통합 테스트 통과
- [ ] 동시성 테스트 (선택)
- [ ] Consumer Lag 확인

---

**Last Updated**: 2024-12-18
