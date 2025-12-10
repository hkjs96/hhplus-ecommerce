# Week 8 Quick Start (3시간 압축 학습)

## ⏱️ 시간이 부족한 수강생을 위한 최소 과제 완료 가이드

**목표:** 3시간 안에 Step 15, Step 16 Pass 조건 충족하기

**전제조건:**
- Spring Boot, JPA 기본 이해
- 트랜잭션 개념 이해
- 시간이 정말 부족한 상황

**학습 스타일:** 개념 이해 < 빠른 구현

---

## 📋 3시간 일정

| 시간 | 단계 | 작업 | 목표 |
|------|------|------|------|
| 0:00-1:00 | 1단계 | 핵심 개념 이해 | 트랜잭션 분리가 왜 필요한지 이해 |
| 1:00-2:00 | 2단계 | Step 15 구현 | Application Event 적용 |
| 2:00-2:40 | 3단계 | Step 16 설계 | 설계 문서 작성 |
| 2:40-3:00 | 4단계 | 검증 및 제출 | Pass 조건 확인 |

---

## 1단계: 핵심 개념 이해 (60분)

### 왜 트랜잭션을 분리해야 하는가? (20분)

**현재 문제:**
```java
@Transactional  // 3.5초 동안 DB Connection 점유
public void processPayment() {
    // DB 작업 (70ms)
    payment.execute();

    // 외부 API 호출 (3초) ⚠️ 문제!
    externalAPI.send();
}
```

**문제점:**
- DB Connection을 3.5초간 점유 → Connection Pool 고갈
- 외부 API 실패 시 전체 롤백 → 과도한 결합
- 최대 TPS: 2.85 (10 connections / 3.5초)

**해결책:**
```java
@Transactional  // 70ms만 DB Connection 점유
public void processPayment() {
    payment.execute(); // DB 작업만
    eventPublisher.publishEvent(new PaymentCompletedEvent());
}

@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCompleted() {
    externalAPI.send(); // 트랜잭션 밖에서 실행
}
```

**효과:**
- 트랜잭션 시간: 3.5초 → 70ms (50배 개선)
- TPS: 2.85 → 142 (50배 개선)

---

### Application Event 기초 (20분)

**3가지만 기억하세요:**

#### 1. 이벤트 정의 (Record 사용)
```java
public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    BigDecimal amount
) {}
```

#### 2. 이벤트 발행 (UseCase에서)
```java
@Service
@RequiredArgsConstructor
public class PaymentUseCase {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void processPayment() {
        // 결제 처리
        eventPublisher.publishEvent(new PaymentCompletedEvent(...));
    }
}
```

#### 3. 이벤트 리스닝 (Listener에서)
```java
@Component
public class PaymentListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        // 후속 작업 처리
    }
}
```

**핵심 규칙:**
- `@TransactionalEventListener` 사용 (정합성 보장)
- `phase = AFTER_COMMIT` (트랜잭션 커밋 후 실행)
- 외부 API 호출은 `@Async` 추가

---

### @TransactionalEventListener Phase (20분)

**4가지 Phase 중 2개만 기억:**

| Phase | 실행 시점 | 주요 용도 |
|-------|----------|----------|
| **AFTER_COMMIT** | 트랜잭션 커밋 후 | 외부 연동, 알림 (90% 사용) |
| AFTER_ROLLBACK | 트랜잭션 롤백 후 | 실패 처리 (10% 사용) |

**예시:**
```java
// AFTER_COMMIT: 커밋 후 실행 (가장 많이 사용)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleSuccess(PaymentCompletedEvent event) {
    externalAPI.send(event); // 트랜잭션 완료 후 안전하게 호출
}

// AFTER_ROLLBACK: 롤백 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
public void handleFailure(PaymentCompletedEvent event) {
    log.error("결제 실패: {}", event);
}
```

---

## 2단계: Step 15 구현 (60분)

### 빠른 구현 체크리스트
- [ ] 이벤트 클래스 2개 정의
- [ ] 기존 UseCase에 이벤트 발행 추가
- [ ] 리스너 클래스 2개 작성
- [ ] AsyncConfig 작성
- [ ] 테스트 1개 작성

---

### 2-1. 이벤트 정의 (10분)

**위치:** `application/payment/event/PaymentCompletedEvent.java`

```java
package io.hhplus.ecommerce.application.payment.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    BigDecimal amount,
    LocalDateTime completedAt
) {}
```

**똑같이 하나 더 만들기:**
- `OrderCompletedEvent` (주문 완료)
- 또는 `CouponIssuedEvent` (쿠폰 발급)

---

### 2-2. UseCase 수정 (15분)

**Before:**
```java
@Service
@RequiredArgsConstructor
public class PaymentUseCase {
    private final DataPlatformClient dataPlatformClient;
    private final NotificationService notificationService;

    @Transactional
    public void processPayment() {
        // 결제 처리
        Payment payment = executePayment();

        // 외부 호출 (문제!)
        dataPlatformClient.send(payment);
        notificationService.send(payment);
    }
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class PaymentUseCase {
    private final ApplicationEventPublisher eventPublisher;
    // dataPlatformClient, notificationService 제거!

    @Transactional
    public void processPayment() {
        // 결제 처리만
        Payment payment = executePayment();

        // 이벤트 발행
        eventPublisher.publishEvent(new PaymentCompletedEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getAmount(),
            LocalDateTime.now()
        ));
    }
}
```

---

### 2-3. Listener 작성 (20분)

**위치:** `application/payment/listener/`

**리스너 1: 데이터 플랫폼**
```java
package io.hhplus.ecommerce.application.payment.listener;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentDataPlatformListener {
    private final DataPlatformClient dataPlatformClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            dataPlatformClient.sendPaymentData(event);
            log.info("데이터 플랫폼 전송 성공: {}", event.paymentId());
        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 실패: {}", event.paymentId(), e);
        }
    }
}
```

**리스너 2: 알림 발송**
```java
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentNotificationListener {
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            notificationService.sendPaymentConfirmation(event);
            log.info("알림 발송 성공: {}", event.paymentId());
        } catch (Exception e) {
            log.error("알림 발송 실패: {}", event.paymentId(), e);
        }
    }
}
```

---

### 2-4. AsyncConfig 작성 (5분)

**위치:** `config/AsyncConfig.java`

```java
package io.hhplus.ecommerce.config;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();
        return executor;
    }
}
```

---

### 2-5. 간단한 테스트 (10분)

```java
@SpringBootTest
class PaymentEventTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockBean
    private DataPlatformClient dataPlatformClient;

    @Test
    void 결제완료_이벤트_발행_및_처리() {
        // given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            1L, 1L, 1L, BigDecimal.valueOf(10000), LocalDateTime.now()
        );

        // when
        eventPublisher.publishEvent(event);

        // then
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(dataPlatformClient).sendPaymentData(any());
        });
    }
}
```

---

## 3단계: Step 16 설계 문서 (40분)

### 빠른 문서 작성 템플릿

**위치:** `docs/week8/TRANSACTION_SEPARATION_DESIGN.md`

```markdown
# 트랜잭션 분리 설계

## 1. 현재 시스템 분석

### 1.1 PaymentUseCase.processPayment()

**트랜잭션 범위:**
- 주문 조회 (10ms)
- 잔액 차감 (20ms)
- 결제 생성 (15ms)
- 재고 차감 (25ms)
- 데이터 플랫폼 전송 (3,000ms) ⚠️
- 알림 발송 (500ms) ⚠️

**총 트랜잭션 시간:** 3,570ms

**문제점:**
1. 외부 API 호출로 트랜잭션 3.5초 유지
2. Connection Pool 고갈 (최대 2.85 TPS)
3. 외부 API 실패 시 전체 롤백

---

## 2. 개선 방안

### 2.1 이벤트 기반 분리

**핵심 원칙:**
- 트랜잭션 내: DB 작업만 (70ms)
- 트랜잭션 외: 외부 API 호출 (이벤트 기반)

**적용:**
- ✅ 주문 조회, 잔액 차감, 결제 생성, 재고 차감 → 트랜잭션 내
- ❌ 데이터 플랫폼 전송, 알림 발송 → AFTER_COMMIT 이벤트

---

## 3. 시퀀스 다이어그램

### Before
\`\`\`
Client → UseCase: processPayment()
UseCase → DB: 결제 처리 (70ms)
UseCase → API: 외부 호출 (3,500ms) ⚠️
UseCase → Client: 응답 (총 3,570ms)
\`\`\`

### After
\`\`\`
Client → UseCase: processPayment()
UseCase → DB: 결제 처리 (70ms)
UseCase → EventBus: publishEvent()
UseCase → Client: 응답 (총 71ms) ✅

EventBus → Listener: handleEvent()
Listener → API: 외부 호출 (비동기)
\`\`\`

---

## 4. 보상 트랜잭션

### 시나리오: 결제 실패 시 쿠폰 복구

**상황:**
1. 쿠폰 사용 (성공)
2. 결제 처리 (실패)

**보상 로직:**
\`\`\`java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) {
    if (event.couponId() != null) {
        userCoupon.restore(); // 쿠폰 상태를 AVAILABLE로 복구
    }
}
\`\`\`

**멱등성 보장:**
- 쿠폰 상태 확인 (USED인 경우만 복구)
- 중복 실행 시 무시

---

## 5. 예상 효과

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 트랜잭션 시간 | 3,570ms | 71ms | 98% ↓ |
| TPS | 2.85 | 140 | 4,912% ↑ |
| Connection Pool 사용률 | 95% | 30% | 68% ↓ |

---

## 6. 리스크 및 대응

### 이벤트 유실
- **리스크:** 프로세스 재시작 시 이벤트 유실
- **대응:** Outbox Pattern (추후 적용)

### 순서 보장
- **리스크:** 비동기 이벤트 순서 보장 안됨
- **대응:** 이벤트 체이닝 또는 순서 의존성 제거
```

**복사-붙여넣기 후 현재 프로젝트에 맞게 수정하세요! (10분)**

---

## 4단계: 검증 및 제출 (20분)

### Step 15 체크리스트
- [ ] ApplicationEventPublisher 주입했는가?
- [ ] @TransactionalEventListener 사용했는가?
- [ ] AFTER_COMMIT phase 적용했는가?
- [ ] 최소 2개 이상의 이벤트 적용했는가?
- [ ] @Async 설정했는가?
- [ ] 기존 기능이 정상 동작하는가?

### Step 16 체크리스트
- [ ] 현재 시스템 분석 포함했는가?
- [ ] 문제점 식별했는가?
- [ ] Before/After 시퀀스 다이어그램 있는가?
- [ ] 보상 트랜잭션 설계 포함했는가?
- [ ] 예상 효과 (성능 개선) 명시했는가?

---

## 💡 시간 절약 팁

### 1. 코드 복사 활용
- PaymentListener를 복사하여 OrderListener 만들기
- 이벤트 클래스도 복사-붙여넣기 후 수정

### 2. 테스트는 최소한만
- 통합 테스트 1개만 작성
- 수동 테스트로 동작 확인

### 3. 설계 문서는 템플릿 활용
- 위 템플릿 복사 후 프로젝트에 맞게 수정
- 시퀀스 다이어그램은 텍스트로도 OK

### 4. 완벽함보다 완성도
- 보상 트랜잭션은 1개만 구현
- 예외 처리는 try-catch + 로깅으로 충분

---

## 🚨 자주 하는 실수 (꼭 확인!)

### 1. @EventListener 사용
```java
// ❌ Bad
@EventListener
public void handleEvent() { }

// ✅ Good
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleEvent() { }
```

### 2. 이벤트에 Setter
```java
// ❌ Bad
@Getter @Setter
public class Event { }

// ✅ Good
public record Event(...) { }
```

### 3. 예외 미처리
```java
// ❌ Bad
@TransactionalEventListener
public void handleEvent() {
    externalAPI.call(); // 예외 발생 시 다른 리스너 실행 안됨
}

// ✅ Good
@Async
@TransactionalEventListener
public void handleEvent() {
    try {
        externalAPI.call();
    } catch (Exception e) {
        log.error("Failed", e);
    }
}
```

---

## 📚 더 깊게 학습하고 싶다면

- [LEARNING_ROADMAP.md](./LEARNING_ROADMAP.md) - 10시간 심화 학습
- [QNA_SUMMARY.md](./QNA_SUMMARY.md) - 코치 Q&A 핵심 정리
- [COMMON_PITFALLS.md](./COMMON_PITFALLS.md) - 자주 하는 실수

---

## ⏰ 시간 배분 실패 시

### 2시간 30분 남았을 때
- Step 15만 집중 (1시간 30분)
- Step 16 최소 문서 (1시간)

### 2시간 남았을 때
- Step 15 필수만 구현 (1시간)
- Step 16 템플릿 복사 (1시간)

### 1시간 30분 남았을 때
- Step 15만 완료 (1시간 30분)
- Step 16은 간단한 문서만

---

**작성일:** 2025-12-10
**버전:** 1.0
**목표:** 최소 시간으로 Pass 조건 충족
