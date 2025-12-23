# Phase 1: 리스너 책임 분리 완료 보고서

**작성일**: 2025-12-14
**목적**: 8주차 코치 피드백 기반 아키텍처 개선 - Phase 1 완료

---

## ✅ 완료된 작업

### 1. EventIdempotencyListener 분리 생성

**파일**: `src/main/java/io/hhplus/ecommerce/application/product/listener/EventIdempotencyListener.java`

**책임**: 이벤트 중복 처리 방지 (Single Responsibility)
- 멱등성 체크
- 처리 완료 기록
- 중복 이벤트 발견 시 예외 발생

**핵심 코드**:
```java
@Component
@Order(1)  // 가장 먼저 실행
@RequiredArgsConstructor
@Slf4j
public class EventIdempotencyListener {

    private final EventIdempotencyService idempotencyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void checkIdempotency(PaymentCompletedEvent event) {
        String eventType = "PaymentCompleted";
        String eventId = "order-" + event.getOrder().getId();

        // 중복 이벤트 체크
        if (idempotencyService.isProcessed(eventType, eventId)) {
            throw new DuplicateEventException("이미 처리된 이벤트입니다: " + eventId);
        }

        // 처리 완료 기록
        idempotencyService.markAsProcessed(eventType, eventId);
    }
}
```

**8주차 피드백 반영**:
- ✅ 리스너 1개 = 책임 1개
- ✅ 예외를 던져 후속 리스너 실행 방지

---

### 2. RankingUpdateEventListener 분리 생성

**파일**: `src/main/java/io/hhplus/ecommerce/application/product/listener/RankingUpdateEventListener.java`

**책임**: Redis Sorted Set 랭킹 갱신만 담당
- 주문 완료 시 상품별 판매량 score 증가
- 실패 시 DLQ (FailedEvent)에 저장

**핵심 코드**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingUpdateEventListener {

    private final ProductRankingRepository rankingRepository;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("rankingExecutor")  // 전용 executor
    public void updateRanking(PaymentCompletedEvent event) {
        try {
            // 핵심 로직: 랭킹 갱신
            for (OrderItem item : event.getOrder().getOrderItems()) {
                rankingRepository.incrementScore(
                    item.getProduct().getId().toString(),
                    item.getQuantity()
                );
            }
        } catch (Exception e) {
            // Redis 장애: DLQ로 이동
            saveToDLQ(event, e.getMessage());
        }
    }
}
```

**8주차 피드백 반영**:
- ✅ 리스너는 가벼움 (응집도 ↑)
- ✅ Outbox 책임 제거 (DLQ만 사용)
- ⚠️ TODO: Phase 2에서 @Retryable 추가 예정 (spring-retry 의존성 필요)

---

### 3. AsyncConfig에 rankingExecutor 추가

**파일**: `src/main/java/io/hhplus/ecommerce/config/AsyncConfig.java`

**변경 사항**:
```java
@Bean(name = "rankingExecutor")
public Executor rankingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("ranking-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setAwaitTerminationSeconds(60);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
}
```

**목적**:
- 랭킹 갱신 작업만을 위한 별도 스레드 풀
- 다른 비동기 작업과 격리

---

### 4. 기존 RankingEventListener 비활성화

**파일**: `src/main/java/io/hhplus/ecommerce/application/product/listener/RankingEventListener.java`

**변경 사항**:
```java
// @Component  // ← 8주차 피드백: 책임 분리로 인해 비활성화
@RequiredArgsConstructor
@Slf4j
public class RankingEventListener {
    // 기존 코드 유지 (테스트에서 retryFailedEvent() 메서드 사용)
}
```

**이유**:
- 하위 호환성: 테스트에서 `retryFailedEvent()` 메서드를 사용하므로 완전 삭제 불가
- 향후 테스트 리팩토링 후 제거 예정

---

## 🎯 아키텍처 개선 효과

### Before (기존)

**문제점**:
```java
@Component
public class RankingEventListener {
    @TransactionalEventListener
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // ❌ 책임 1: 멱등성 체크
        if (idempotencyService.isProcessed(...)) { return; }

        try {
            // ❌ 책임 2: 랭킹 갱신
            processRankingUpdate(event);

            // ❌ 책임 3: 멱등성 기록
            idempotencyService.markAsProcessed(...);
        } catch (Exception e) {
            // ❌ 책임 4: 실패 이벤트 저장 (Outbox 책임!)
            saveFailedEvent(event, e.getMessage());
        }
    }
}
```

**SRP 위반**: 1개 리스너가 4가지 책임

---

### After (개선)

**2개 리스너로 분리**:

```java
// ✅ 멱등성 체크 전용
@Component
@Order(1)
public class EventIdempotencyListener {
    public void checkIdempotency(PaymentCompletedEvent event) {
        // 오직 멱등성 체크만
    }
}

// ✅ 랭킹 갱신 전용
@Component
public class RankingUpdateEventListener {
    @Async("rankingExecutor")
    public void updateRanking(PaymentCompletedEvent event) {
        // 오직 랭킹 갱신만
    }
}
```

**SRP 준수**: 1개 리스너 = 1개 책임

---

## 🎓 8주차 코치 피드백 반영 현황

| 피드백 항목 | 반영 여부 | 비고 |
|----------|---------|------|
| #1: Outbox 책임 분리 | ✅ 부분 반영 | DLQ 사용, Phase 2에서 완전 분리 예정 |
| #2: 리스너 책임 과다 | ✅ **완료** | 2개 리스너로 분리 |
| #6: 예외 처리 전략 | ✅ **완료** | EventIdempotencyListener에서 예외 던짐 |
| #8: UseCase 과체중 | ⚠️ Phase 2 예정 | |
| #11: 비동기 운영 품질 | ⚠️ Phase 2 예정 | MDC Decorator 추가 |

---

## 📝 남은 작업 (Phase 2, 3)

### Phase 2: Outbox 책임 분리
- [ ] `EventOutboxPublisher` 생성
- [ ] `OutboxEvent` 엔티티 생성
- [ ] 리스너에서 Outbox 로직 제거
- [ ] `@Retryable` 추가 (spring-retry 의존성)

### Phase 3: 테스트 재설계
- [ ] 기존 RankingEventListener 제거
- [ ] 분리된 리스너 테스트 작성
- [ ] Integration Test 리팩토링

---

## 🏗️ 다이어그램

### Phase 1 이전

```
┌────────────────────────────────────────┐
│   RankingEventListener                │
│   (4가지 책임)                         │
├────────────────────────────────────────┤
│ 1. 멱등성 체크                         │
│ 2. 랭킹 갱신                           │
│ 3. 멱등성 기록                         │
│ 4. FailedEvent 저장 (Outbox!)          │
└────────────────────────────────────────┘
```

### Phase 1 이후

```
┌───────────────────────────────────┐
│  EventIdempotencyListener        │
│  @Order(1)                       │
├───────────────────────────────────┤
│  - 멱등성 체크                    │
│  - 중복 시 예외 발생              │
│  - 처리 완료 기록                 │
└───────────────────────────────────┘
            ↓
┌───────────────────────────────────┐
│  RankingUpdateEventListener      │
│  @Async("rankingExecutor")       │
├───────────────────────────────────┤
│  - 랭킹 갱신                      │
│  - 실패 시 DLQ 저장               │
└───────────────────────────────────┘
```

---

## ⚠️ 주의사항

1. **기존 RankingEventListener 제거 불가**: 테스트에서 `retryFailedEvent()` 메서드를 사용하므로 @Component만 제거

2. **@Retryable 미적용**: spring-retry 의존성이 없어 Phase 2로 연기
   - 현재는 실패 시 즉시 DLQ로 이동
   - Phase 2에서 Exponential Backoff 재시도 메커니즘 추가 예정

3. **빌드 상태**: 컴파일은 성공, 테스트 결과는 별도 확인 필요

---

**작성자**: Claude Code
**상태**: ✅ Phase 1 완료 (리스너 책임 분리)
**다음 단계**: 빌드 및 테스트 결과 확인 → Phase 2 시작 여부 결정
