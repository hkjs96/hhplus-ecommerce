# Week 8: 이벤트 기반 아키텍처 개선 계획

**작성일**: 2025-12-14
**목적**: 8주차 피드백 반영한 이벤트/트랜잭션/Outbox 개선 로드맵

---

## 📊 현재 상황 분석

### ✅ 잘 구현된 부분
1. **`@TransactionalEventListener(AFTER_COMMIT)` 도입** ✅
   - `RankingEventListener`, `DataPlatformEventListener`에서 부가 작업 분리
   - 메인 비즈니스(결제)의 응답 지연 최소화

2. **이벤트 멱등성 (Redis 기반)** ✅
   - `EventIdempotencyService`로 중복 이벤트 방지
   - TTL 7일로 메모리 효율성 고려

3. **재시도 메커니즘 (FailedEvent + Outbox 패턴)** ✅
   - FailedEvent 엔티티로 실패 기록
   - Exponential Backoff (1min → 2min → 4min)
   - DLQ (FAILED 상태) 도입

4. **Unit Test 멱등성 보장** ✅
   - RankingEventListenerTest 5/5 통과
   - Mock 의존성 완벽 관리

### ❌ 개선 필요한 부분 (8주차 피드백 기반)

| 우선순위 | 문제점 | 현재 코드 위치 | 8주차 피드백 항목 |
|------|------|----------|--------------|
| **1** | Outbox를 서비스 로직에 섞어서 응집도 하락 | `RankingEventListener` | #1: Outbox 책임 분리 |
| **2** | 리스너가 너무 많은 책임 가짐 | `RankingEventListener` (멱등성+재시도+랭킹갱신) | #4: 리스너 책임 과다 |
| **3** | 예외를 다 잡아먹어 재시도 무력화 | `RankingEventListener.handlePaymentCompleted()` | #6: 예외 처리 전략 |
| **4** | `@Transactional(REQUIRES_NEW)` 남용 | 현재는 없지만 추가 시 주의 | #3: REQUIRES_NEW 오해 |
| **5** | 실패 처리 전략 빈약 | 현재 DLQ만 있음 (배치 재처리 없음) | #5: 실패 처리 전략 |
| **6** | 동기 통신 타임아웃 대응 부족 | DataPlatformClient (현재 Feign timeout만) | #7: 동기 통신 대응 |
| **7** | ProcessPaymentUseCase 과체중 | 이벤트 발행 + 비즈니스 로직 혼재 | #8: UseCase 레이어 과체중 |
| **8** | 비동기 운영 품질 (MDC, 로그) | 현재 MDC Decorator 없음 | #11: 비동기 운영 품질 |

---

## 🎯 개선 우선순위 및 실행 계획

### Phase 1: 구조 개선 (High Priority)

#### 1.1 Outbox 책임 분리 (우선순위 #1)

**문제:**
```java
// ❌ 현재: 리스너가 FailedEvent 저장 책임까지 가짐
@TransactionalEventListener(AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    try {
        // 멱등성 체크
        // 랭킹 갱신
    } catch (Exception e) {
        // FailedEvent 저장 (책임 과다!)
        saveFailedEvent(event, e.getMessage());
    }
}
```

**개선안:**
```java
// ✅ 개선: 별도 Outbox Publisher로 캡슐화
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
    }
}

// 리스너는 가볍게
@TransactionalEventListener(AFTER_COMMIT)
@Async
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    if (idempotencyService.isProcessed(eventType, eventId)) {
        return;
    }

    processRankingUpdate(event);  // 핵심 로직만
    idempotencyService.markAsProcessed(eventType, eventId);
}
```

**변경 파일:**
- 신규: `EventOutboxPublisher.java`
- 신규: `OutboxEvent.java` (Entity)
- 신규: `OutboxEventRepository.java`
- 수정: `RankingEventListener.java` (FailedEvent 저장 로직 제거)

---

#### 1.2 리스너 책임 분리 (우선순위 #2)

**문제:**
```java
// ❌ 현재: RankingEventListener가 3가지 책임
// 1. 멱등성 체크
// 2. 랭킹 갱신
// 3. 실패 처리 (FailedEvent 저장)
```

**개선안:**
```java
// ✅ 개선: 리스너 2개로 분리
@Component
public class RankingUpdateEventListener {
    @TransactionalEventListener(AFTER_COMMIT)
    @Async("rankingExecutor")  // 전용 executor
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // 책임 1: 랭킹 갱신만
        updateProductRanking(event);
    }
}

@Component
public class EventIdempotencyListener {
    @TransactionalEventListener(AFTER_COMMIT)
    @Order(1)  // 먼저 실행
    public void checkIdempotency(PaymentCompletedEvent event) {
        // 책임 2: 멱등성 체크만
        if (idempotencyService.isProcessed(eventType, eventId)) {
            throw new DuplicateEventException();  // 중복이면 예외
        }
        idempotencyService.markAsProcessed(eventType, eventId);
    }
}
```

**장점:**
- 재시도/DLQ 범위 명확
- 각 리스너가 1가지 책임만
- 테스트 용이성 ↑

**변경 파일:**
- 신규: `RankingUpdateEventListener.java`
- 신규: `EventIdempotencyListener.java`
- 삭제: `RankingEventListener.java` (분리)

---

#### 1.3 예외 처리 전략 개선 (우선순위 #3)

**문제:**
```java
// ❌ 현재: 예외를 잡아먹어 @Retryable 무력화
catch (Exception e) {
    log.error("랭킹 갱신 실패", e);
    saveFailedEvent(event, e.getMessage());  // ← 예외를 던지지 않음!
}
```

**개선안:**
```java
// ✅ 개선: 예외를 던져 재시도 작동
@TransactionalEventListener(AFTER_COMMIT)
@Async
@Retryable(
    value = RedisConnectionException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    try {
        updateProductRanking(event);
    } catch (RedisConnectionException e) {
        log.warn("Redis 일시적 장애, 재시도 예정", e);
        throw e;  // ← 예외를 던져야 @Retryable 작동!
    } catch (Exception e) {
        log.error("복구 불가 에러, DLQ로 이동", e);
        // DLQ 저장 후 정상 종료 (재시도 X)
        dlqService.save(event, e.getMessage());
    }
}
```

**변경 파일:**
- 수정: `RankingUpdateEventListener.java` (예외 처리 개선)
- 신규: `DLQService.java`

---

### Phase 2: 운영 품질 개선 (Medium Priority)

#### 2.1 비동기 운영 품질 (우선순위 #8)

**개선사항:**
1. **MDC Propagation**: TraceId/RequestId 전파
2. **스레드풀 세분화**: 도메인별 executor 분리
3. **Rejected Policy**: DLQ로 이동 (동기화 방지)

```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "rankingExecutor")
    public Executor rankingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ranking-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("랭킹 작업 큐 포화, DLQ로 이동");
            // DLQ 저장
        });
        executor.setTaskDecorator(new MdcTaskDecorator());  // ← MDC 전파
        executor.initialize();
        return executor;
    }

    @Bean(name = "dataplatformExecutor")
    public Executor dataplatformExecutor() {
        // 별도 스레드풀
    }
}
```

**변경 파일:**
- 수정: `AsyncConfig.java` (MDC Decorator, Rejected Policy)
- 신규: `MdcTaskDecorator.java`

---

#### 2.2 동기 통신 타임아웃 대응 (우선순위 #6)

**현재:**
```java
// ❌ Feign timeout만 설정, 재시도/fallback 없음
@FeignClient(
    name = "data-platform",
    url = "${external.dataplatform.url}",
    configuration = FeignConfig.class
)
```

**개선안:**
```java
// ✅ Resilience4j 적용
@Service
public class DataPlatformClient {

    @CircuitBreaker(
        name = "dataplatform",
        fallbackMethod = "sendOrderDataFallback"
    )
    @Retry(name = "dataplatform")
    @Timeout(value = 3, timeUnit = TimeUnit.SECONDS)
    public void sendOrderData(OrderDataRequest request) {
        feignClient.send(request);
    }

    private void sendOrderDataFallback(OrderDataRequest request, Exception e) {
        log.warn("데이터 플랫폼 장애, DLQ로 이동", e);
        dlqService.save("DataPlatform", request, e.getMessage());
    }
}
```

**변경 파일:**
- 수정: `DataPlatformClient.java` (Resilience4j 적용)
- 추가: `application.yml` (Resilience4j 설정)

---

### Phase 3: 테스트 개선 (Low Priority)

#### 3.1 Integration Test 수정

**현재 문제:**
1. **TransactionTemplate 사용 시 detached entity** → ID null 문제
2. **ProcessPaymentUseCaseIntegrationTest에서 entityManager.flush() 문제**

**해결책:**
```java
// ✅ @Transactional 클래스 레벨 사용 (flush() 불필요)
@SpringBootTest
@Transactional  // ← 클래스 레벨
class ProcessPaymentUseCaseIntegrationTest {

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.create(...));
        testProduct = productRepository.save(Product.create(...));
        // flush() 불필요! @Transactional이 관리
    }
}
```

**변경 파일:**
- 수정: `ProcessPaymentUseCaseIntegrationTest.java` (EntityManager 제거, @Transactional 추가)
- 수정: `RankingEventIdempotencyTest.java` (Order.create() 호출 시 testUser 사용)

---

## 🚀 실행 순서

### Week 1 (현재 주)
- [ ] **Phase 1.1**: Outbox 책임 분리 (EventOutboxPublisher 도입)
- [ ] **Phase 1.2**: 리스너 책임 분리 (2개로 분리)
- [ ] **Phase 3.1**: Integration Test 수정 (TransactionTemplate 제거)

### Week 2 (다음 주)
- [ ] **Phase 1.3**: 예외 처리 전략 개선
- [ ] **Phase 2.1**: 비동기 운영 품질 (MDC, Rejected Policy)

### Week 3 (선택)
- [ ] **Phase 2.2**: 동기 통신 타임아웃 대응 (Resilience4j)
- [ ] 전체 테스트 통과율 80% 이상 달성

---

## 📋 8주차 피드백 매핑

| 피드백 항목 | 우선순위 | Phase | 완료 예정 |
|----------|--------|-------|---------|
| #1: Outbox 책임 분리 | High | 1.1 | Week 1 |
| #2: 리스너 책임 과다 | High | 1.2 | Week 1 |
| #3: REQUIRES_NEW 오해 | Medium | - | (현재 해당 없음) |
| #4: 리스너 책임 과다 | High | 1.2 | Week 1 |
| #5: 실패 처리 전략 | Medium | 1.3 | Week 2 |
| #6: 예외 처리 전략 | High | 1.3 | Week 2 |
| #7: 동기 통신 대응 | Medium | 2.2 | Week 3 |
| #8: UseCase 레이어 과체중 | Low | - | (다음 리팩토링) |
| #11: 비동기 운영 품질 | Medium | 2.1 | Week 2 |

---

## 🎓 핵심 원칙 (8주차 코치 피드백)

1. **이벤트/Outbox/Saga를 "코드로만 붙이는 것"이 아니라, 실패·지연·재시도·DLQ·멱등성·타임아웃까지 포함한 운영 가능한 설계**
2. **메인 도메인 서비스는 가볍게**, 이벤트 발행/저장/전송 책임은 **분리/추상화**
3. **리스너 1개 = 책임 1개**, 같은 이벤트를 여러 리스너가 구독
4. **예외를 던져야 재시도 작동**, 로그만 남기고 잡아먹으면 무력화
5. **Outbox는 "메인 트랜잭션 안에서 발행해야 할 메시지 저장"**, 실제 발행은 별도 흐름

---

**작성자**: Claude Code
**상태**: ✅ **개선 계획 수립 완료**
**다음 단계**: Phase 1.1 Outbox 책임 분리 구현
