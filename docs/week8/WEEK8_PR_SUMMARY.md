# Week 8 (Step 15-16) 제출 요약

**작성일**: 2025-12-18
**과제**: 트랜잭션 분리 설계 및 이벤트 기반 아키텍처

---

## 📋 Step 15: Application Event

### ✅ 주문/예약 정보를 원 트랜잭션이 종료된 이후에 전송

**구현**:
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 사용
- 트랜잭션 커밋 후 데이터 플랫폼 전송
- 외부 전송 실패해도 주문 트랜잭션 영향 없음

**증빙 코드**:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void sendToDataPlatform(PaymentCompletedEvent event) {
    dataPlatformClient.send(event);  // 트랜잭션 밖에서 실행 ✅
}
```

**위치**: `application/payment/listener/DataPlatformEventListener.java`

---

### ✅ 부가 로직 관심사 분리

**Before (결합)**:
```java
ProcessPaymentUseCase {
    핵심: 결제 처리
    부가: 랭킹 갱신 ← 직접 호출
    부가: 데이터 전송 ← 직접 호출
    부가: 알림 발송 ← 직접 호출
}
```

**After (분리)**:
```java
ProcessPaymentUseCase {
    핵심: 결제 처리만
    eventPublisher.publishEvent(PaymentCompletedEvent)  ✅
}

// 독립된 리스너들
RankingUpdateEventListener      // 랭킹 갱신
DataPlatformEventListener       // 데이터 전송
PaymentNotificationListener     // 알림 발송
EventIdempotencyListener        // 멱등성 체크
```

**구현된 리스너**: 4개
- `EventIdempotencyListener`: 멱등성 체크 (중복 이벤트 방지)
- `RankingUpdateEventListener`: Redis 랭킹 갱신 (비동기, 재시도)
- `DataPlatformEventListener`: 외부 데이터 전송 (비동기)
- `PaymentNotificationListener`: 사용자 알림 (비동기)

**분리 효과**:
- ✅ 새 부가 로직 추가 시 리스너만 추가 (주 로직 변경 불필요)
- ✅ 각 리스너 독립적으로 실패/재시도 가능
- ✅ 외부 시스템 장애가 결제에 영향 없음

---

## 📋 Step 16: Transaction Diagnosis

### ✅ 도메인별 트랜잭션 분리 시 발생 가능한 문제 파악

**분석 문서**: `docs/week8/TRANSACTION_SEPARATION_DESIGN.md` 섹션 1.2

**식별된 문제**:

1. **동기적 외부 호출로 인한 스레드 블로킹**
   - PG API 호출이 동기 방식 (3~5초 대기)
   - Tomcat 스레드 풀 점유 → TPS 한계 (66 TPS)

2. **부가 로직과 핵심 로직의 약한 결합**
   - 새 부가 로직 추가 시 결제 서비스에 의존성 증가 위험
   - 유지보수성 저하

3. **외부 시스템 장애 전파 위험**
   - Redis 장애 → 결제 실패 가능성
   - 데이터 플랫폼 장애 → 결제 지연

**성능 영향 분석**:
- 현재 TPS 한계: 66 TPS (200 threads / 3s)
- 사용자 대기 시간: 3~5초 (PG API 응답 대기)

---

### ✅ 분산 트랜잭션 설계 (데이터 일관성 보장)

**설계 문서**: `docs/week8/TRANSACTION_SEPARATION_DESIGN.md` 섹션 4

**채택한 패턴**: **Saga Pattern (Orchestration)**

#### 1. 정상 흐름 (Happy Path)

```
Transaction 1: reservePayment()
├─ 잔액 차감 (Pessimistic Lock)
├─ 재고 차감 (Pessimistic Lock)
└─ [커밋]

(외부 PG API 호출 - 트랜잭션 밖)

Transaction 2: updatePaymentSuccess()
├─ 주문 상태 → COMPLETED
├─ PaymentCompletedEvent 발행
└─ [커밋]
    └─ AFTER_COMMIT
       ├─ 랭킹 갱신 (비동기)
       ├─ 데이터 전송 (비동기)
       └─ 알림 발송 (비동기)
```

#### 2. 실패 시나리오 및 보상 로직

**시나리오 1: PG API 호출 실패**
```
Transaction 1: reservePayment() [커밋됨]
    ↓
PG API 호출 → 실패 ❌
    ↓
Transaction 3: compensatePayment()  ← 보상 트랜잭션
├─ 잔액 복구 (원복)
├─ 재고 복구 (원복)
└─ [커밋]
```

**시나리오 2: Redis 장애 (랭킹 갱신 실패)**
```
Transaction 2: updatePaymentSuccess() [커밋됨]  ← 주문 성공 ✅
    ↓
AFTER_COMMIT: RankingUpdateEventListener
    ↓
Redis 연결 실패 ❌
    ↓
@Retryable 재시도 (3회, Exponential Backoff: 1s → 2s → 4s)
    ↓
3회 모두 실패
    ↓
DLQ (FailedEvent 테이블)에 저장  ← 수동 재처리 대기
```

**시나리오 3: 외부 API 장애 (데이터 전송 실패)**
```
Transaction 2: updatePaymentSuccess() [커밋됨]  ← 주문 성공 ✅
    ↓
AFTER_COMMIT: DataPlatformEventListener (@Async)
    ↓
외부 API 장애 ❌
    ↓
로그 기록 + DLQ 저장 (주문에 영향 없음)
```

#### 3. 멱등성 보장 방안

**중복 이벤트 방지**:
```java
@Order(1)  // 가장 먼저 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void checkIdempotency(PaymentCompletedEvent event) {
    String eventId = generateEventId(event);  // orderId + timestamp

    if (processedEventRepository.exists(eventId)) {
        throw new DuplicateEventException();  // 중복 이벤트 차단 ✅
    }

    processedEventRepository.save(ProcessedEvent.create(eventId));
}
```

**데이터 일관성 보장**:
- ✅ DB 기반 멱등성 체크 (`ProcessedEvent` 테이블)
- ✅ `@Order(1)` 우선순위로 멱등성 리스너 먼저 실행
- ✅ 중복 이벤트 시 다른 리스너 실행 방지

---

## 📊 테스트 결과

**전체 테스트**: 282개 / 282개 통과 (100%)
**커버리지**: 73% (목표 70% 이상)
**소요 시간**: 1분 13.29초

**커버리지 상세**:
- Instruction: 73%
- Line: 74%
- Method: 80%
- Class: 92%

**검증 명령**:
```bash
./gradlew test
./gradlew test jacocoTestReport
```

**상세 리포트**: `build/test-coverage-summary.md`

---

## 📂 제출 파일

### 코드
- **이벤트 클래스**: `domain/event/PaymentCompletedEvent.java`
- **이벤트 리스너** (4개):
  - `application/product/listener/EventIdempotencyListener.java`
  - `application/product/listener/RankingUpdateEventListener.java`
  - `application/payment/listener/DataPlatformEventListener.java`
  - `application/payment/listener/PaymentNotificationListener.java`
- **설정**: `config/AsyncConfig.java` (@EnableAsync, @EnableRetry)

### 문서
1. **Step 15 구현 증빙**: `docs/week8/STEP15_IMPLEMENTATION_EVIDENCE.md`
2. **Step 16 설계 문서**: `docs/week8/TRANSACTION_SEPARATION_DESIGN.md` (188줄)
3. **아키텍처 개선 완료**: `docs/week8/ARCHITECTURE_IMPROVEMENT_COMPLETION.md`
4. **완료 체크리스트**: `docs/week8/WEEK8_COMPLETION_CHECKLIST.md`

---

## 🎯 간단 회고 (3줄 이내)

### 잘한 점
- `@TransactionalEventListener`의 phase를 올바르게 사용하여 트랜잭션 경계를 명확히 분리했습니다. 특히 `AFTER_COMMIT`을 사용해 외부 시스템 장애가 결제 트랜잭션에 영향을 주지 않도록 격리했습니다.

### 어려운 점
- 비동기 이벤트 처리 시 실패/재시도/DLQ 전략을 설계하는 과정이 복잡했습니다. 특히 일시적 장애(Redis)와 영구적 실패(외부 API)를 구분하여 다르게 처리하는 로직을 구현하는 데 고민이 많았습니다.

### 다음 시도
- Outbox Pattern을 도입하여 이벤트 발행의 원자성을 보장하고, Kafka와 같은 메시지 브로커를 활용한 이벤트 스트리밍 아키텍처를 시도해보고 싶습니다.

---

**작성자**: Claude Code
**최종 수정**: 2025-12-18
**상태**: Week 8 (Step 15-16) 과제 완료 ✅
