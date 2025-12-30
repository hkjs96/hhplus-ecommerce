# Week 8: 트랜잭션 분리 설계 및 이벤트 기반 아키텍처

## 📋 개요

**학습 기간:** 5일 (10시간 학습 기준) 또는 3시간 압축 학습
**핵심 목표:** 트랜잭션 경계 설정 및 Application Event를 활용한 도메인 간 결합도 낮추기

### 과제 구성
- **Step 15 (구현)**: Application Event를 활용한 이벤트 기반 아키텍처 구현
- **Step 16 (설계)**: 트랜잭션 분리 설계 문서 작성

---

## 🎯 학습 목표

### 1. 트랜잭션 경계 이해
- 트랜잭션의 적절한 범위 설정
- 긴 트랜잭션의 문제점 파악
- 서비스 간 트랜잭션 분리 전략

### 2. 이벤트 기반 아키텍처
- Application Event와 Domain Event의 차이
- `ApplicationEventPublisher` 활용
- `@TransactionalEventListener`의 phase 이해

### 3. 보상 트랜잭션 (Saga Pattern)
- 분산 환경에서의 데이터 정합성 보장
- Orchestration vs Choreography
- 보상 트랜잭션 설계 및 구현

### 4. MSA 전환 준비
- 모놀리식에서 MSA로의 단계적 전환
- 도메인 분리 및 서비스 경계 설정
- 이벤트 기반 통신 패턴

---

## 📚 주요 문서

### 학습 자료
- [**LEARNING_ROADMAP.md**](./LEARNING_ROADMAP.md) - 5일 학습 로드맵 (10시간)
- [**QUICK_START.md**](./QUICK_START.md) - 3시간 압축 학습 가이드
- [**QNA_SUMMARY.md**](./QNA_SUMMARY.md) - 코치 Q&A 핵심 정리

### 구현 가이드
- [**STEP15_IMPLEMENTATION.md**](./STEP15_IMPLEMENTATION.md) - Application Event 구현 가이드
- [**STEP16_DESIGN.md**](./STEP16_DESIGN.md) - 트랜잭션 분리 설계 가이드
- [**EVENT_BASED_REFACTORING.md**](./EVENT_BASED_REFACTORING.md) - 이벤트 패턴 및 Best Practices

### 참고 자료
- [**TRANSACTION_SEPARATION_DESIGN.md**](./TRANSACTION_SEPARATION_DESIGN.md) - 트랜잭션 경계 설정 가이드
- [**QNA_SUMMARY.md**](./QNA_SUMMARY.md#3-%EB%B3%B4%EC%83%81-%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%98--saga-pattern) - Saga 패턴 상세 설명
- [**COMMON_PITFALLS.md**](./COMMON_PITFALLS.md) - 자주 하는 실수 및 해결책

---

## 🔑 핵심 개념

### Application Event vs Domain Event

| 구분 | Application Event | Domain Event |
|------|-------------------|--------------|
| **정의** | 애플리케이션 레벨 이벤트 | 비즈니스 도메인 이벤트 |
| **목적** | 계층/모듈 간 결합도 감소 | 도메인 로직 표현 |
| **발행 위치** | UseCase (Application Layer) | Entity/Service (Domain Layer) |
| **예시** | `OrderCompletedEvent` | `ProductStockChanged` |
| **프레임워크** | Spring ApplicationEventPublisher | 직접 구현 또는 도메인 라이브러리 |

### @TransactionalEventListener Phase

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleEvent(OrderCompletedEvent event) {
    // 트랜잭션 커밋 후 실행
}
```

| Phase | 실행 시점 | 주요 용도 |
|-------|----------|----------|
| **BEFORE_COMMIT** | 커밋 직전 | 트랜잭션 내 추가 검증 |
| **AFTER_COMMIT** | 커밋 성공 후 | 외부 시스템 연동, 알림 발송 |
| **AFTER_ROLLBACK** | 롤백 후 | 실패 로깅, 알림 |
| **AFTER_COMPLETION** | 완료 후 (성공/실패 무관) | 리소스 정리 |

### Saga Pattern 선택 기준

**Orchestration (오케스트레이션)**
- 중앙 관리자가 각 단계를 순차 실행
- 복잡한 워크플로우에 적합
- 장점: 명확한 제어 흐름, 쉬운 디버깅
- 단점: 중앙 관리자가 SPOF

**Choreography (코레오그래피)**
- 각 서비스가 이벤트를 주고받으며 협력
- 단순한 워크플로우에 적합
- 장점: 높은 자율성, 확장성
- 단점: 추적 어려움, 순환 의존성 위험

---

## 📊 평가 기준

### Step 15: Application Event 구현 (Pass 조건)

✅ **필수 구현**
- [ ] `ApplicationEventPublisher`를 사용한 이벤트 발행
- [ ] `@TransactionalEventListener`를 사용한 이벤트 처리
- [ ] 최소 2개 이상의 도메인에 이벤트 적용
- [ ] 트랜잭션 경계가 명확히 분리됨
- [ ] 기존 기능이 정상 동작함 (회귀 테스트 통과)

✅ **코드 품질**
- [ ] 이벤트 클래스가 불변 객체로 설계됨
- [ ] 이벤트 네이밍이 과거형으로 작성됨 (예: `OrderCompletedEvent`)
- [ ] 순환 참조가 발생하지 않음
- [ ] 적절한 예외 처리가 구현됨

### Step 16: 트랜잭션 분리 설계 (Pass 조건)

✅ **설계 문서**
- [ ] 현재 시스템의 트랜잭션 경계 분석
- [ ] 문제점 식별 (긴 트랜잭션, 불필요한 결합 등)
- [ ] 개선 방안 제시 (이벤트 분리, 비동기 처리 등)
- [ ] 트랜잭션 흐름도 (시퀀스 다이어그램 등)

✅ **보상 트랜잭션 설계**
- [ ] 실패 시나리오 식별
- [ ] 보상 로직 설계
- [ ] Saga 패턴 선택 근거
- [ ] 데이터 정합성 보장 방안

---

## 🚀 Quick Start

### 1. 3시간 압축 학습 (최소 과제 완료)
```
1시간: QUICK_START.md → 핵심 개념 이해
1시간: STEP15_IMPLEMENTATION.md → 코드 구현
1시간: STEP16_DESIGN.md → 설계 문서 작성
```

### 2. 10시간 심화 학습 (권장)
```
Day 1 (2시간): 트랜잭션 & 이벤트 개념
Day 2 (2시간): Application Event 실습
Day 3 (2시간): 보상 트랜잭션 & Saga
Day 4 (2시간): 트랜잭션 분리 설계
Day 5 (2시간): 문서 작성 & 리뷰
```

상세 일정은 [LEARNING_ROADMAP.md](./LEARNING_ROADMAP.md) 참조

---

## 💡 주요 학습 포인트

### 1. 트랜잭션은 짧게 유지하라
**왜?** 긴 트랜잭션은 락 홀딩 시간을 증가시켜 동시성을 저하시킴

**Before (Bad)**
```java
@Transactional
public void processOrder(OrderRequest request) {
    // 1. 주문 생성 (DB 쓰기)
    Order order = createOrder(request);

    // 2. 재고 차감 (DB 쓰기)
    decreaseStock(order.getItems());

    // 3. 결제 처리 (외부 API 호출 - 3초 소요)
    paymentGateway.charge(order.getAmount());

    // 4. 알림 발송 (외부 API 호출 - 2초 소요)
    notificationService.sendOrderConfirmation(order);
}
// 총 트랜잭션 시간: 5초 이상 (DB 락 유지)
```

**After (Good)**
```java
@Transactional
public Long processOrder(OrderRequest request) {
    // 1. 주문 생성 및 재고 차감만 트랜잭션 내 처리
    Order order = createOrder(request);
    decreaseStock(order.getItems());

    // 2. 이벤트 발행 (AFTER_COMMIT)
    eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));

    return order.getId();
}
// 트랜잭션 시간: 100ms 이하

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    // 외부 연동은 트랜잭션 밖에서 처리
    paymentGateway.charge(event.getOrderId());
    notificationService.sendOrderConfirmation(event.getOrderId());
}
```

### 2. 이벤트는 불변 객체로 설계하라
```java
// Good: 불변 객체
public record OrderCompletedEvent(
    Long orderId,
    Long userId,
    BigDecimal totalAmount,
    LocalDateTime completedAt
) {}

// Bad: 가변 객체
@Getter @Setter
public class OrderCompletedEvent {
    private Long orderId;
    private Long userId;
    // ... setter로 인한 예상치 못한 변경 가능
}
```

### 3. 이벤트 리스너는 멱등성을 보장하라
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // Bad: 중복 실행 시 문제 발생
    loyaltyService.addPoints(event.getUserId(), 100);

    // Good: 멱등성 보장
    if (!loyaltyService.hasPointsAdded(event.getOrderId())) {
        loyaltyService.addPoints(event.getUserId(), 100);
    }
}
```

---

## 🔗 외부 참고 자료

### 필수 학습
- [AWS Summit Seoul 2023 - 이벤트 기반 MSA 구축](https://www.youtube.com/watch?v=b65zIH7sDug)
- [[SLASH 24] 보상 트랜잭션으로 분산 환경에서도 안전하게 환전하기](https://toss.im/slash-24/sessions/24)

### 추가 학습
- [Martin Fowler - Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
- [Microservices.io - Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Spring Event Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)

---

## ❓ FAQ

### Q1: Application Event와 Message Queue(RabbitMQ, Kafka)의 차이는?
**A:** Application Event는 **단일 애플리케이션 내부**에서 모듈 간 통신에 사용되며, 프로세스가 재시작되면 유실됩니다. Message Queue는 **서비스 간 통신**에 사용되며 영속성과 재시도를 보장합니다.

| 구분 | Application Event | Message Queue |
|------|-------------------|---------------|
| 범위 | 단일 프로세스 | 분산 시스템 |
| 영속성 | 없음 (메모리) | 있음 (디스크) |
| 재시도 | 수동 구현 | 자동 지원 |
| 순서 보장 | 보장 안됨 | 보장 가능 |
| 용도 | 모듈 간 결합도 감소 | 서비스 간 통신 |

### Q2: @TransactionalEventListener를 사용하지 않으면?
**A:** 일반 `@EventListener`는 이벤트 발행 즉시 실행됩니다. 트랜잭션이 롤백되어도 리스너가 실행되므로 **데이터 정합성 문제**가 발생할 수 있습니다.

```java
// Bad: 트랜잭션 롤백되어도 알림 발송됨
@EventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    notificationService.send("주문 완료!");
}

// Good: 트랜잭션 커밋 후 알림 발송
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    notificationService.send("주문 완료!");
}
```

### Q3: 이벤트 리스너 실행 순서를 제어할 수 있나?
**A:** `@Order` 어노테이션으로 우선순위 지정 가능하지만, **순서에 의존하는 설계는 지양**해야 합니다.

```java
@Order(1)
@TransactionalEventListener
public void firstListener(OrderCompletedEvent event) { }

@Order(2)
@TransactionalEventListener
public void secondListener(OrderCompletedEvent event) { }
```

### Q4: 이벤트 리스너에서 예외가 발생하면?
**A:** 기본적으로 예외 전파로 인해 다른 리스너가 실행되지 않습니다. `@Async` + 적절한 예외 처리로 격리해야 합니다.

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    try {
        externalService.notify(event);
    } catch (Exception e) {
        log.error("알림 발송 실패", e);
        // 재시도 큐에 적재 또는 별도 처리
    }
}
```

---

## 🎓 학습 체크리스트

### 개념 이해
- [ ] 트랜잭션 경계와 범위를 설명할 수 있다
- [ ] Application Event의 용도와 장점을 이해했다
- [ ] @TransactionalEventListener의 각 Phase를 구분할 수 있다
- [ ] Saga 패턴의 두 가지 방식을 비교할 수 있다
- [ ] 보상 트랜잭션의 필요성을 이해했다

### 구현 능력
- [ ] ApplicationEventPublisher로 이벤트를 발행할 수 있다
- [ ] @TransactionalEventListener로 이벤트를 처리할 수 있다
- [ ] 이벤트 기반으로 도메인 간 결합도를 낮출 수 있다
- [ ] 보상 트랜잭션을 설계하고 구현할 수 있다
- [ ] 비동기 이벤트 처리 시 예외를 처리할 수 있다

### 설계 능력
- [ ] 현재 시스템의 트랜잭션 경계를 분석할 수 있다
- [ ] 긴 트랜잭션의 문제점을 식별할 수 있다
- [ ] 트랜잭션 분리 전략을 제시할 수 있다
- [ ] 시퀀스 다이어그램으로 흐름을 표현할 수 있다
- [ ] 실패 시나리오를 식별하고 대응할 수 있다

---

## 📞 도움이 필요하면

- 코치 Q&A 내용: [QNA_SUMMARY.md](./QNA_SUMMARY.md)
- 자주 하는 실수: [COMMON_PITFALLS.md](./COMMON_PITFALLS.md)
- 커뮤니티 토론방 활용
- 코치 멘토링 세션 참여

---

**작성일:** 2025-12-10
**버전:** 1.0
**다음 과제:** Week 9 - MSA 전환 및 분산 트랜잭션
