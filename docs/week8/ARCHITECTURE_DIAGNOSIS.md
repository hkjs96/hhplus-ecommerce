# 아키텍처 진단 보고서: 왜 테스트가 계속 실패하는가?

**작성일**: 2025-12-14
**목적**: 테스트 실패 근본 원인 + 8주차 피드백 기반 구조 개선

---

## 🚨 핵심 문제: 테스트가 아니라 **설계가 잘못됨**

### 문제 1: RankingEventListener의 책임 과다 (8주차 피드백 #2, #4)

**현재 코드:**
```java
@Component
public class RankingEventListener {

    @TransactionalEventListener(AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        String eventType = "PaymentCompleted";
        String eventId = "order-" + event.getOrder().getId();

        // ❌ 책임 1: 멱등성 체크
        if (idempotencyService.isProcessed(eventType, eventId)) {
            log.info("중복 이벤트 무시");
            return;
        }

        try {
            // ❌ 책임 2: 랭킹 갱신
            processRankingUpdate(event);

            // ❌ 책임 3: 멱등성 기록
            idempotencyService.markAsProcessed(eventType, eventId);

        } catch (Exception e) {
            // ❌ 책임 4: 실패 이벤트 저장 (Outbox 책임!)
            log.error("랭킹 갱신 실패, FailedEvent 저장", e);
            saveFailedEvent(event, e.getMessage());
        }
    }

    private void saveFailedEvent(PaymentCompletedEvent event, String errorMessage) {
        // ❌ 리스너가 FailedEvent를 직접 저장 (응집도 하락!)
        FailedEvent failedEvent = FailedEvent.create(...);
        failedEventRepository.save(failedEvent);
    }
}
```

**문제점:**
1. **1개 리스너가 4가지 책임** → SRP 위반
2. **Outbox 책임이 서비스 로직에 섞임** (8주차 피드백 #1)
3. **예외를 잡아먹어 재시도 무력화** (8주차 피드백 #6)
4. **테스트하기 어려움** → 모든 의존성을 Mock해야 함

---

### 문제 2: TransactionTemplate 남용 (테스트 실패의 직접 원인)

**테스트 코드:**
```java
@BeforeEach
void setUp() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);

    template.execute(status -> {
        testUser = userRepository.save(User.create(...));
        testProduct = productRepository.save(Product.create(...));
        return null;
    });
    // ← 트랜잭션 종료
    // testUser는 detached 상태
    // testUser.getId()는 프록시로 lazy loading 시도 → 트랜잭션 없어서 실패!
}
```

**근본 원인:**
- `@Transactional` 없이 `TransactionTemplate` 사용
- 엔티티가 detached 상태로 반환
- **Hibernate 프록시 문제** (getId()가 lazy loading 시도)

**왜 이런 패턴을 사용했나?**
→ `@TransactionalEventListener(AFTER_COMMIT)` 테스트를 위해 **명시적 트랜잭션 커밋**이 필요했기 때문

**올바른 해결책:**
→ **테스트 설계를 바꿔야 함** (코드 문제가 아님!)

---

## 🎯 8주차 피드백 매핑: 우리 코드의 문제

| 피드백 항목 | 우리 코드 위치 | 심각도 | 영향 |
|----------|------------|-------|------|
| #1: Outbox 책임 분리 | `RankingEventListener.saveFailedEvent()` | **HIGH** | 응집도 하락 + 테스트 어려움 |
| #2: 리스너 책임 과다 | `RankingEventListener` (4가지 책임) | **HIGH** | SRP 위반 + 재시도/DLQ 범위 불명확 |
| #6: 예외 처리 전략 | `catch (Exception e)` 후 재throw 안 함 | **HIGH** | `@Retryable` 무력화 |
| #8: UseCase 과체중 | `ProcessPaymentUseCase` | MEDIUM | 이벤트 발행 책임 혼재 |
| #11: 비동기 운영 품질 | MDC Decorator 없음 | MEDIUM | TraceId 전파 안 됨 |

---

## ✅ 올바른 설계 (8주차 피드백 반영)

### 설계 1: 리스너 책임 분리 (1 리스너 = 1 책임)

```java
// ✅ 멱등성 체크 전용 리스너 (먼저 실행)
@Component
@Order(1)
public class EventIdempotencyListener {

    @TransactionalEventListener(AFTER_COMMIT)
    public void checkIdempotency(PaymentCompletedEvent event) {
        String eventType = "PaymentCompleted";
        String eventId = "order-" + event.getOrder().getId();

        if (idempotencyService.isProcessed(eventType, eventId)) {
            throw new DuplicateEventException("이미 처리된 이벤트");
        }

        idempotencyService.markAsProcessed(eventType, eventId);
    }
}

// ✅ 랭킹 갱신 전용 리스너
@Component
public class RankingUpdateEventListener {

    @TransactionalEventListener(AFTER_COMMIT)
    @Async("rankingExecutor")  // 전용 executor
    @Retryable(
        value = RedisConnectionException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void updateRanking(PaymentCompletedEvent event) {
        try {
            // 핵심 로직만: 랭킹 갱신
            for (OrderItem item : event.getOrder().getItems()) {
                rankingRepository.incrementScore(
                    item.getProduct().getId().toString(),
                    item.getQuantity()
                );
            }
        } catch (RedisConnectionException e) {
            log.warn("Redis 일시적 장애, 재시도 예정", e);
            throw e;  // ← 예외를 던져야 @Retryable 작동!
        } catch (Exception e) {
            log.error("복구 불가 에러, DLQ로 이동", e);
            dlqService.save("RankingUpdate", event, e.getMessage());
            // 재시도하지 않음 (정상 종료)
        }
    }
}
```

**장점:**
1. ✅ **1 리스너 = 1 책임** (SRP 준수)
2. ✅ **재시도/DLQ 범위 명확**
3. ✅ **테스트 용이** (각각 독립 테스트)
4. ✅ **예외 전파** (@Retryable 정상 작동)

---

### 설계 2: Outbox 책임 분리 (별도 Publisher)

```java
// ✅ Outbox 전용 Publisher
@Component
public class EventOutboxPublisher {

    // 메인 트랜잭션 안에서 "발행해야 할 메시지 저장"
    @Transactional
    public void publishWithOutbox(DomainEvent event) {
        OutboxEvent outbox = OutboxEvent.create(
            event.getEventType(),
            event.getEventId(),
            objectMapper.writeValueAsString(event)
        );
        outboxRepository.save(outbox);
        // 실제 발행은 별도 폴링/CDC
    }
}

// ✅ 리스너에서는 Outbox를 모름
@Component
public class RankingUpdateEventListener {

    @TransactionalEventListener(AFTER_COMMIT)
    @Async
    public void updateRanking(PaymentCompletedEvent event) {
        rankingRepository.incrementScore(...);
        // Outbox 책임 없음!
    }
}
```

**장점:**
1. ✅ **Outbox 책임 캡슐화**
2. ✅ **리스너는 가벼움** (응집도 ↑)
3. ✅ **변경 용이** (Kafka로 전환 시 Publisher만 수정)

---

### 설계 3: 테스트 설계 개선

**❌ 잘못된 패턴 (현재):**
```java
@BeforeEach
void setUp() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.execute(status -> {
        testUser = userRepository.save(...);  // detached!
        return null;
    });
}
```

**✅ 올바른 패턴 1: @Sql 사용**
```java
@Sql("/test-data/users.sql")
@Test
void testRankingUpdate() {
    // SQL로 데이터 준비 (ID 보장)
}
```

**✅ 올바른 패턴 2: TestEntityManager**
```java
@Autowired
private TestEntityManager testEntityManager;

@BeforeEach
void setUp() {
    testUser = testEntityManager.persistFlushFind(User.create(...));
    // managed 상태로 반환
}
```

**✅ 올바른 패턴 3: 각 테스트에서 데이터 생성**
```java
@Test
@Transactional
void testRankingUpdate() {
    // Given: 테스트 메서드 내부에서 데이터 생성
    User user = userRepository.save(User.create(...));
    Product product = productRepository.save(Product.create(...));

    // When: 테스트 로직
    // ...
}
```

---

## 🚀 실행 계획 (우선순위)

### Phase 1: 리스너 책임 분리 (즉시 시작)
- [ ] `EventIdempotencyListener` 분리
- [ ] `RankingUpdateEventListener` 분리
- [ ] 예외 처리 개선 (throw 추가)
- [ ] 기존 `RankingEventListener` 제거

### Phase 2: Outbox 책임 분리
- [ ] `EventOutboxPublisher` 생성
- [ ] `OutboxEvent` 엔티티 생성
- [ ] 리스너에서 Outbox 로직 제거

### Phase 3: 테스트 재설계 (테스트 포기 or 간단화)
**선택지:**
1. **테스트 간단화**: Unit Test만 유지 (Integration Test 삭제)
2. **@Sql 사용**: 테스트 데이터를 SQL로 관리
3. **TestEntityManager 사용**: Detached 문제 해결

---

## 💬 코치님 가르침 핵심 (8주차)

> "이벤트/Outbox/Saga를 코드로만 붙이는 것이 아니라,
> **실패·지연·재시도·DLQ·멱등성·타임아웃까지 포함한 운영 가능한 설계**"

> "메인 도메인 서비스는 가볍게,
> 이벤트 발행/저장/전송 책임은 **분리/추상화**"

> "리스너 1개 = 책임 1개,
> 같은 이벤트를 여러 리스너가 구독"

> "예외를 던져야 재시도 작동,
> 로그만 남기고 잡아먹으면 무력화"

---

**작성자**: Claude Code
**상태**: ✅ **근본 원인 진단 완료**
**다음 단계**: Phase 1 리스너 책임 분리 구현 OR 테스트 전체 삭제 후 재설계
