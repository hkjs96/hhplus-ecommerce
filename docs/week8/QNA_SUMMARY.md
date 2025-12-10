# Week 8 Q&A 핵심 정리

## 📋 목차
1. [트랜잭션 경계 & 분리 전략](#1-트랜잭션-경계--분리-전략)
2. [Application Event 활용](#2-application-event-활용)
3. [보상 트랜잭션 & Saga Pattern](#3-보상-트랜잭션--saga-pattern)
4. [MSA 전환 전략](#4-msa-전환-전략)
5. [기술적 구현 상세](#5-기술적-구현-상세)
6. [실전 케이스 스터디](#6-실전-케이스-스터디)

---

## 1. 트랜잭션 경계 & 분리 전략

### Q1-1: 트랜잭션을 왜 분리해야 하나요?
**코치 답변 (제이):**
> 트랜잭션은 **데이터 정합성**을 보장하는 최소 단위여야 합니다. 긴 트랜잭션은 다음 문제를 유발합니다:
>
> 1. **락 홀딩 시간 증가** → 동시성 저하
> 2. **Connection Pool 고갈** → 다른 요청 대기
> 3. **데드락 가능성 증가**
>
> 특히 외부 API 호출을 트랜잭션 안에 두면, 외부 시스템의 응답 시간만큼 DB 락을 잡고 있게 됩니다.

**실제 예시:**
```java
// Bad: 5초 트랜잭션 (외부 API 3초 포함)
@Transactional
public void processPayment(PaymentCommand command) {
    payment.execute(); // DB 쓰기
    externalAPI.send(); // 3초 대기 (DB 락 유지)
    ranking.update(); // Redis
}
// Connection Pool 크기 10 → 최대 2 TPS

// Good: 100ms 트랜잭션 (핵심만)
@Transactional
public void processPayment(PaymentCommand command) {
    payment.execute(); // DB 쓰기만
    eventPublisher.publishEvent(new PaymentCompletedEvent(...));
}
// → 최대 100 TPS (50배 개선)
```

### Q1-2: 어디까지를 한 트랜잭션으로 묶어야 하나요?
**코치 답변 (제이):**
> **ACID가 보장되어야 하는 최소 범위**만 트랜잭션으로 묶으세요.
>
> **트랜잭션에 포함할 것:**
> - 핵심 비즈니스 로직 (주문 생성, 재고 차감, 결제)
> - 데이터 정합성이 즉시 보장되어야 하는 작업
>
> **트랜잭션에서 제외할 것:**
> - 외부 API 호출 (데이터 플랫폼, 알림 서비스)
> - 캐시/랭킹 업데이트 (Redis 등)
> - 로깅, 통계 집계
> - 알림 발송

**판단 기준:**
```
"이 작업이 실패하면 이전 작업도 롤백되어야 하는가?"
→ Yes: 트랜잭션 내 포함
→ No: 트랜잭션 외부 (이벤트 분리)
```

### Q1-3: 트랜잭션 분리 후 데이터 정합성은 어떻게 보장하나요?
**코치 답변 (로이):**
> **즉시 정합성(Immediate Consistency)**에서 **최종 정합성(Eventual Consistency)**으로 전환합니다.
>
> 1. **핵심 데이터**: 즉시 정합성 보장 (트랜잭션 내)
>    - 주문 상태, 재고 수량, 결제 금액
>
> 2. **부가 데이터**: 최종 정합성 허용 (이벤트 기반)
>    - 랭킹 점수, 포인트 적립, 알림 발송
>
> 3. **보상 메커니즘**:
>    - 이벤트 실패 시 재시도
>    - Dead Letter Queue (DLQ)
>    - 모니터링 & 수동 복구

**예시:**
```java
// 핵심 트랜잭션 (즉시 정합성)
@Transactional
public void processPayment(Long orderId) {
    order.complete();
    stock.decrease();
    payment.create();
    // 여기까지는 원자적으로 커밋되어야 함
}

// 부가 작업 (최종 정합성)
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    ranking.update(event.getProductId()); // 실패해도 주문은 유효
    loyalty.addPoints(event.getUserId()); // 나중에 재시도 가능
}
```

---

## 2. Application Event 활용

### Q2-1: Application Event와 Domain Event의 차이는?
**코치 답변 (제이):**
> **Application Event (Spring):**
> - **목적**: 애플리케이션 레이어 간 결합도 감소
> - **발행 위치**: UseCase (Application Layer)
> - **구현**: Spring ApplicationEventPublisher
> - **영속성**: 없음 (메모리, 프로세스 재시작 시 유실)
> - **예시**: `OrderCompletedEvent`, `PaymentSucceededEvent`
>
> **Domain Event (DDD):**
> - **목적**: 도메인 로직 표현, 비즈니스 의미 전달
> - **발행 위치**: Entity/Aggregate (Domain Layer)
> - **구현**: 직접 구현 또는 도메인 라이브러리
> - **영속성**: Event Sourcing 시 영속화
> - **예시**: `ProductStockChanged`, `CouponIssued`

**우리 과제에서는 Application Event만 사용합니다.**

### Q2-2: @EventListener vs @TransactionalEventListener 차이?
**코치 답변 (제이):**
> **@EventListener:**
> - 이벤트 발행 즉시 실행 (트랜잭션 커밋 전)
> - 트랜잭션 롤백되어도 리스너 실행됨
> - → **데이터 정합성 문제 발생**
>
> **@TransactionalEventListener:**
> - 트랜잭션 완료 후 실행 (phase 지정 가능)
> - 커밋 실패 시 리스너 실행 안됨
> - → **정합성 보장**

**잘못된 사용 예:**
```java
// Bad: @EventListener 사용
@Service
public class OrderService {
    @Transactional
    public void createOrder(OrderCommand command) {
        Order order = orderRepository.save(new Order(...));
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));

        // 이후 검증 실패로 예외 발생
        if (!isValid(order)) {
            throw new InvalidOrderException();
        }
        // 트랜잭션 롤백!
    }
}

@Component
public class NotificationListener {
    @EventListener // 문제!
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 트랜잭션 롤백되었지만 알림은 발송됨
        notificationService.sendOrderConfirmation(event.getOrderId());
    }
}
```

**올바른 사용:**
```java
@Component
public class NotificationListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 트랜잭션 커밋 후에만 실행
        notificationService.sendOrderConfirmation(event.getOrderId());
    }
}
```

### Q2-3: @TransactionalEventListener의 phase는 언제 사용하나요?
**코치 답변 (로이):**

| Phase | 실행 시점 | 주요 용도 | 예시 |
|-------|----------|----------|------|
| **BEFORE_COMMIT** | 커밋 직전 (트랜잭션 내) | 추가 검증, 데이터 수정 | 재고 최종 확인 |
| **AFTER_COMMIT** | 커밋 성공 후 | 외부 연동, 알림 발송 | 결제 완료 알림 |
| **AFTER_ROLLBACK** | 롤백 후 | 실패 로깅, 보상 처리 | 주문 실패 알림 |
| **AFTER_COMPLETION** | 완료 후 (성공/실패 무관) | 리소스 정리 | 임시 파일 삭제 |

**실전 예시:**
```java
// BEFORE_COMMIT: 커밋 전 최종 검증
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void validateBeforeCommit(OrderCreatedEvent event) {
    // 아직 트랜잭션 내부이므로 검증 실패 시 롤백 가능
    if (!stockService.isAvailable(event.getOrderId())) {
        throw new InsufficientStockException();
    }
}

// AFTER_COMMIT: 커밋 후 외부 연동
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 트랜잭션 완료 확정 → 외부 시스템 호출 안전
    dataPlatformClient.sendOrderData(event);
}

// AFTER_ROLLBACK: 실패 처리
@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
public void handleOrderFailed(OrderCreatedEvent event) {
    log.error("주문 실패: {}", event.getOrderId());
    notificationService.sendOrderFailure(event.getUserId());
}
```

### Q2-4: 이벤트 리스너 실행 순서를 제어할 수 있나요?
**코치 답변 (제이):**
> `@Order` 어노테이션으로 우선순위 지정은 가능하지만, **순서에 의존하는 설계는 지양**하세요.
> 각 리스너는 **독립적으로 실행 가능**하도록 설계해야 합니다.

```java
// 가능하지만 권장하지 않음
@Order(1)
@TransactionalEventListener
public void firstListener(OrderCompletedEvent event) {
    // 먼저 실행
}

@Order(2)
@TransactionalEventListener
public void secondListener(OrderCompletedEvent event) {
    // 나중에 실행 (firstListener의 결과에 의존 X)
}
```

**더 나은 방식:**
```java
// 의존성이 있다면 이벤트 체이닝
@TransactionalEventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 작업 수행 후 다음 이벤트 발행
    loyalty.addPoints(event);
    eventPublisher.publishEvent(new PointsAddedEvent(...));
}

@TransactionalEventListener
public void handlePointsAdded(PointsAddedEvent event) {
    // 독립적으로 실행
    notificationService.sendPointsNotification(event);
}
```

### Q2-5: 이벤트 리스너에서 예외가 발생하면?
**코치 답변 (로이):**
> 기본적으로 **예외가 전파**되어 다른 리스너가 실행되지 않습니다.
> **@Async + try-catch**로 격리하세요.

```java
// Bad: 예외 전파로 다른 리스너 실행 안됨
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    externalService.notify(event); // 예외 발생 시 아래 리스너들 실행 안됨
}

// Good: @Async + 예외 처리로 격리
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    try {
        externalService.notify(event);
    } catch (Exception e) {
        log.error("알림 발송 실패", e);
        dlqService.enqueue("payment-notification", event);
    }
}
```

---

## 3. 보상 트랜잭션 & Saga Pattern

### Q3-1: 보상 트랜잭션이 필요한 이유는?
**코치 답변 (제이):**
> 분산 환경에서는 **2PC(Two-Phase Commit)**가 비현실적입니다.
>
> **2PC의 문제:**
> - 성능 저하 (모든 참여자 대기)
> - 가용성 문제 (하나라도 응답 없으면 전체 블록)
> - 장애 전파 (한 서비스 장애 → 전체 서비스 블록)
>
> **보상 트랜잭션 접근:**
> - 각 단계를 개별 트랜잭션으로 커밋
> - 실패 시 이전 단계를 **취소하는 트랜잭션** 실행
> - 물리적 롤백 X, 논리적 롤백 O

**예시: 항공권 예약**
```
1. 항공권 예약 (성공, 커밋)
2. 결제 처리 (성공, 커밋)
3. 좌석 배정 (실패!)

보상 트랜잭션:
3. (없음)
2. 결제 취소 (환불)
1. 항공권 예약 취소
```

### Q3-2: 보상 트랜잭션 설계 시 주의사항은?
**코치 답변 (로이):**
> 1. **멱등성(Idempotency) 보장**
>    - 같은 보상을 여러 번 실행해도 결과 동일
>
> 2. **역순 보상**
>    - 일반적으로 작업의 역순으로 보상
>
> 3. **보상 실패 처리**
>    - 보상도 실패할 수 있음 → Dead Letter Queue
>
> 4. **타임아웃 설정**
>    - 무한 대기 방지

**멱등성 예시:**
```java
// Bad: 멱등하지 않음
public void compensateStockDecrease(Long productId, int quantity) {
    product.increaseStock(quantity); // 중복 실행 시 재고 과다 복구
}

// Good: 멱등성 보장
public void compensateStockDecrease(Long orderId, Long productId, int quantity) {
    if (compensationRepository.isAlreadyCompensated(orderId, productId)) {
        return; // 이미 보상됨
    }
    product.increaseStock(quantity);
    compensationRepository.save(new Compensation(orderId, productId));
}
```

### Q3-3: Orchestration vs Choreography 선택 기준은?
**코치 답변 (제이):**

| 상황 | 권장 방식 | 이유 |
|------|----------|------|
| 복잡한 워크플로우 (5단계 이상) | Orchestration | 제어 흐름 명확, 디버깅 쉬움 |
| 단순한 워크플로우 (2-3단계) | Choreography | 결합도 낮음, 확장성 높음 |
| 단계 간 조건 분기가 많음 | Orchestration | 중앙에서 제어 용이 |
| 각 단계가 독립적 | Choreography | 자율성 높음 |
| 트랜잭션 추적 중요 | Orchestration | 상태 관리 용이 |

**우리 과제 (이커머스):**
- 주문 생성 → 재고 차감 → 결제 → 알림 (4단계)
- 조건 분기 적음
- **권장: Choreography** (이벤트 체이닝)

### Q3-4: Saga 패턴 구현 시 상태 관리는 어떻게 하나요?
**코치 답변 (로이):**
> **Orchestration:**
> - Saga 인스턴스가 상태 관리
> - DB 또는 메모리에 저장
>
> **Choreography:**
> - 각 Entity가 자신의 상태 관리
> - 이벤트로 상태 변화 전달

**Orchestration 예시:**
```java
@Entity
public class OrderSaga {
    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private SagaStatus status; // STARTED, STOCK_DECREASED, PAYMENT_COMPLETED, COMPLETED

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @OneToMany(cascade = CascadeType.ALL)
    private List<SagaStep> steps; // 각 단계별 상태 추적
}

@Service
public class OrderSagaOrchestrator {
    public void executeOrderSaga(OrderCommand command) {
        OrderSaga saga = new OrderSaga();
        saga.setStatus(SagaStatus.STARTED);
        sagaRepository.save(saga);

        try {
            // Step 1
            orderService.createOrder(command);
            saga.addStep(new SagaStep("CREATE_ORDER", SUCCESS));

            // Step 2
            stockService.decreaseStock(command.getItems());
            saga.addStep(new SagaStep("DECREASE_STOCK", SUCCESS));
            saga.setStatus(SagaStatus.STOCK_DECREASED);

            // ...
        } catch (Exception e) {
            saga.setStatus(SagaStatus.FAILED);
            compensate(saga);
        }
    }
}
```

**Choreography 예시:**
```java
@Entity
public class Order {
    @Enumerated(EnumType.STRING)
    private OrderStatus status; // PENDING, STOCK_CONFIRMED, PAYMENT_COMPLETED
}

// 각 서비스가 자신의 상태 + 이벤트 발행
@TransactionalEventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    stockService.decreaseStock(event);
    // 성공 시 이벤트 발행
    eventPublisher.publishEvent(new StockDecreasedEvent(...));
}

@TransactionalEventListener
public void handleStockDecreased(StockDecreasedEvent event) {
    Order order = orderRepository.findById(event.getOrderId());
    order.setStatus(OrderStatus.STOCK_CONFIRMED);
    orderRepository.save(order);

    // 다음 단계 이벤트
    eventPublisher.publishEvent(new ReadyForPaymentEvent(...));
}
```

---

## 4. MSA 전환 전략

### Q4-1: 언제 MSA로 전환해야 하나요?
**코치 답변 (제이):**
> **MSA는 은탄환이 아닙니다.** 다음 신호가 보일 때 고려하세요:
>
> 1. **팀 규모**: 20명 이상 (독립적인 팀 운영 필요)
> 2. **배포 빈도**: 각 모듈의 배포 주기가 다름
> 3. **확장성**: 일부 모듈만 스케일링 필요
> 4. **기술 다양성**: 각 모듈이 다른 기술 스택 필요
> 5. **장애 격리**: 일부 장애가 전체 시스템에 영향
>
> **모놀리식이 적합한 경우:**
> - 10명 이하의 소규모 팀
> - 트래픽이 크지 않음
> - 도메인 경계가 불명확
> - MSA 운영 경험 부족

### Q4-2: 모놀리식에서 MSA로 단계적 전환 방법은?
**코치 답변 (로이):**
> **0단계: 모놀리식 (현재)**
> - 모든 기능이 하나의 애플리케이션
>
> **1단계: 모듈러 모놀리식 (이번 과제 목표)**
> - 도메인별로 패키지 분리
> - 이벤트로 모듈 간 통신
> - 배포는 여전히 하나
>
> **2단계: 분산 모놀리식 (중급)**
> - 일부 모듈을 별도 프로세스로 분리
> - API Gateway 도입
> - Message Queue 도입 (RabbitMQ, Kafka)
>
> **3단계: 완전한 MSA (고급)**
> - 각 도메인이 독립 서비스
> - 서비스 메시, 분산 트레이싱
> - CQRS, Event Sourcing

**이번 과제에서는 1단계(모듈러 모놀리식)를 목표로 합니다.**

### Q4-3: 도메인 분리 기준은?
**코치 답변 (제이):**
> **Bounded Context (DDD)**를 기준으로 분리하세요.
>
> **이커머스 예시:**
> 1. **Order Context**: 주문, 주문 아이템
> 2. **Product Context**: 상품, 재고
> 3. **Payment Context**: 결제, 환불
> 4. **User Context**: 사용자, 인증
> 5. **Loyalty Context**: 포인트, 쿠폰
> 6. **Notification Context**: 알림, 이메일
>
> **분리 기준:**
> - 각 Context가 독립적인 비즈니스 의미
> - 변경 이유가 다름 (Single Responsibility)
> - 배포 주기가 다를 가능성

### Q4-4: 이벤트 기반 통신의 단점은?
**코치 답변 (로이):**
> **장점:**
> - 결합도 낮음
> - 확장성 높음
> - 장애 격리
>
> **단점:**
> 1. **복잡도 증가**
>    - 디버깅 어려움
>    - 흐름 추적 어려움
>
> 2. **최종 정합성**
>    - 즉시 정합성 X
>    - 타이밍 이슈 가능
>
> 3. **이벤트 유실 가능성**
>    - 재시도/DLQ 필요
>    - 멱등성 보장 필요
>
> 4. **테스트 복잡**
>    - 비동기 테스트 어려움
>    - 순서 보장 테스트 어려움

**대응 방안:**
- 분산 트레이싱 (Zipkin, Jaeger)
- 이벤트 로그 수집 (ELK Stack)
- Dead Letter Queue (DLQ)
- Circuit Breaker

---

## 5. 기술적 구현 상세

### Q5-1: @Async 사용 시 Thread Pool 설정은?
**코치 답변 (제이):**
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 핵심 설정
        executor.setCorePoolSize(5);    // 기본 스레드 수
        executor.setMaxPoolSize(10);    // 최대 스레드 수
        executor.setQueueCapacity(100); // 대기 큐 크기

        // 추가 설정
        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.initialize();
        return executor;
    }
}
```

**설정 기준:**
- **CorePoolSize**: CPU 코어 수 or 동시 이벤트 처리 예상 수
- **MaxPoolSize**: 피크 시 예상 동시 요청 수
- **QueueCapacity**: 버스트 트래픽 대응

**주의:**
- 너무 큰 Thread Pool → 메모리 낭비, Context Switching 증가
- 너무 작은 Pool → 대기 시간 증가, RejectedExecutionException

### Q5-2: 이벤트 유실을 방지하려면?
**코치 답변 (로이):**
> **Application Event는 메모리 기반** → 프로세스 재시작 시 유실
>
> **유실 방지 전략:**
> 1. **Outbox Pattern**
>    - 이벤트를 DB 테이블에 저장
>    - 별도 스케줄러가 발행
>
> 2. **Message Queue 활용** (고급)
>    - RabbitMQ, Kafka
>    - 영속성 + 재시도 보장
>
> 3. **이벤트 소싱** (고급)
>    - 모든 이벤트를 이벤트 저장소에 보관
>    - 재생 가능

**Outbox Pattern 예시:**
```java
@Entity
public class EventOutbox {
    @Id @GeneratedValue
    private Long id;

    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    private PublishStatus status; // PENDING, PUBLISHED, FAILED

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private int retryCount;
}

// 트랜잭션 내 Outbox 저장
@Transactional
public void processPayment(PaymentCommand command) {
    Payment payment = executePayment(command);

    // 이벤트를 DB에 저장 (트랜잭션에 포함)
    EventOutbox outbox = new EventOutbox(
        "PaymentCompletedEvent",
        toJson(new PaymentCompletedEvent(payment.getId())),
        PublishStatus.PENDING
    );
    outboxRepository.save(outbox);
}

// 별도 스케줄러가 주기적으로 발행
@Scheduled(fixedDelay = 1000)
public void publishPendingEvents() {
    List<EventOutbox> pending = outboxRepository.findByStatus(PENDING);
    for (EventOutbox outbox : pending) {
        try {
            eventPublisher.publishEvent(fromJson(outbox.getPayload()));
            outbox.setStatus(PUBLISHED);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            outbox.incrementRetryCount();
            if (outbox.getRetryCount() > 3) {
                outbox.setStatus(FAILED);
            }
            outboxRepository.save(outbox);
        }
    }
}
```

### Q5-3: 이벤트 순서를 보장하려면?
**코치 답변 (제이):**
> **Application Event는 순서 보장 안됨** (특히 @Async 사용 시)
>
> **순서 보장이 필요하면:**
> 1. **동기 처리** (@Async 제거)
> 2. **@Order 사용** (비권장)
> 3. **이벤트 체이닝**
> 4. **Message Queue 활용** (Kafka Partition Key)
>
> **하지만 대부분의 경우 순서 의존성을 제거하는 것이 더 나은 설계입니다.**

**이벤트 체이닝 예시:**
```java
// Bad: 순서 의존
@TransactionalEventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    loyalty.addPoints(event); // 먼저 실행되어야 함
}

@TransactionalEventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    notification.send("포인트 적립 완료"); // 위 리스너 이후 실행 필요
}

// Good: 이벤트 체이닝
@TransactionalEventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    int points = loyalty.addPoints(event);
    eventPublisher.publishEvent(new PointsAddedEvent(event.getUserId(), points));
}

@TransactionalEventListener
public void handlePointsAdded(PointsAddedEvent event) {
    notification.send("포인트 " + event.getPoints() + "점 적립!");
}
```

### Q5-4: 이벤트 리스너 테스트는 어떻게 하나요?
**코치 답변 (로이):**
```java
@SpringBootTest
class PaymentEventIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockBean
    private NotificationService notificationService;

    @Test
    void 결제완료_이벤트_발행_시_알림_발송() {
        // given
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 10000L);

        // when
        eventPublisher.publishEvent(event);

        // then (비동기이므로 await)
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(notificationService).sendPaymentConfirmation(1L);
        });
    }

    @Test
    @Transactional
    void 트랜잭션_롤백_시_이벤트_리스너_실행_안됨() {
        // given
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 10000L);

        // when
        assertThatThrownBy(() -> {
            eventPublisher.publishEvent(event);
            throw new RuntimeException("강제 롤백");
        }).isInstanceOf(RuntimeException.class);

        // then
        await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verifyNoInteractions(notificationService); // 리스너 실행 안됨
        });
    }
}
```

---

## 6. 실전 케이스 스터디

### Case 1: 주문 취소 시 보상 트랜잭션
**상황:**
```
1. 주문 생성 (DB 커밋)
2. 재고 차감 (DB 커밋)
3. 결제 처리 (외부 API - 실패!)
→ 1, 2를 어떻게 원복할 것인가?
```

**코치 솔루션 (제이):**
```java
// 1. 이벤트 정의
public record OrderCreatedEvent(Long orderId, List<OrderItem> items) {}
public record OrderCancelledEvent(Long orderId, String reason) {}

// 2. 주문 생성
@Transactional
public Long createOrder(OrderCommand command) {
    Order order = orderRepository.save(new Order(command));
    eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), order.getItems()));
    return order.getId();
}

// 3. 재고 차감 (AFTER_COMMIT)
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    try {
        stockService.decreaseStock(event.items());
        eventPublisher.publishEvent(new StockDecreasedEvent(event.orderId()));
    } catch (InsufficientStockException e) {
        // 재고 부족 시 주문 취소
        orderService.cancelOrder(event.orderId(), "재고 부족");
        eventPublisher.publishEvent(new OrderCancelledEvent(event.orderId(), "재고 부족"));
    }
}

// 4. 결제 처리
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleStockDecreased(StockDecreasedEvent event) {
    try {
        paymentService.charge(event.orderId());
    } catch (PaymentFailedException e) {
        // 결제 실패 시 보상 트랜잭션
        stockService.increaseStock(event.orderId()); // 재고 복구
        orderService.cancelOrder(event.orderId(), "결제 실패");
        eventPublisher.publishEvent(new OrderCancelledEvent(event.orderId(), "결제 실패"));
    }
}

// 5. 주문 취소 알림
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleOrderCancelled(OrderCancelledEvent event) {
    notificationService.sendOrderCancellation(event.orderId(), event.reason());
}
```

### Case 2: 쿠폰 발급 후 결제 실패
**상황:**
```
1. 쿠폰 발급 (DB 커밋)
2. 주문 생성 (DB 커밋)
3. 결제 처리 (실패!)
→ 쿠폰을 어떻게 복구할 것인가?
```

**코치 솔루션 (로이):**
```java
// 1. 쿠폰 사용
@Transactional
public void useCoupon(Long userId, Long couponId, Long orderId) {
    UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
    userCoupon.use(orderId); // 상태를 USED로 변경
    userCouponRepository.save(userCoupon);
}

// 2. 결제 실패 시 쿠폰 복구
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) {
    if (event.getCouponId() != null) {
        try {
            compensateCouponUsage(event.getUserId(), event.getCouponId(), event.getOrderId());
        } catch (Exception e) {
            log.error("쿠폰 복구 실패", e);
            // DLQ에 적재
            dlqService.enqueue("coupon-compensation", event);
        }
    }
}

@Transactional
public void compensateCouponUsage(Long userId, Long couponId, Long orderId) {
    UserCoupon userCoupon = userCouponRepository
        .findByUserIdAndCouponIdAndOrderId(userId, couponId, orderId);

    // 멱등성 체크
    if (userCoupon.getStatus() != CouponStatus.USED) {
        return; // 이미 복구됨
    }

    userCoupon.restore(); // 상태를 AVAILABLE로 변경
    userCouponRepository.save(userCoupon);
}
```

### Case 3: 데이터 플랫폼 전송 실패
**상황:**
```
1. 결제 완료 (DB 커밋)
2. 데이터 플랫폼 전송 (외부 API - 실패!)
→ 재시도? DLQ? 포기?
```

**코치 솔루션 (제이):**
```java
// 1. 데이터 플랫폼 전송 (비동기 + 재시도)
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    sendDataPlatformWithRetry(event, 0);
}

private void sendDataPlatformWithRetry(PaymentCompletedEvent event, int retryCount) {
    try {
        dataPlatformClient.sendPaymentData(event);
        log.info("데이터 플랫폼 전송 성공: orderId={}", event.getOrderId());
    } catch (Exception e) {
        if (retryCount < 3) {
            log.warn("데이터 플랫폼 전송 실패 - 재시도 {}/3", retryCount + 1);
            // 지수 백오프 (1초, 2초, 4초)
            sleep(Duration.ofSeconds((long) Math.pow(2, retryCount)));
            sendDataPlatformWithRetry(event, retryCount + 1);
        } else {
            log.error("데이터 플랫폼 전송 최종 실패: orderId={}", event.getOrderId(), e);
            // DLQ에 적재 (수동 처리)
            dlqService.enqueue("data-platform", event);
        }
    }
}
```

---

## 📌 핵심 정리

### 반드시 기억할 원칙
1. **트랜잭션은 짧게** - 외부 API는 트랜잭션 밖으로
2. **@TransactionalEventListener 사용** - 정합성 보장
3. **이벤트는 불변 객체** - Record 또는 final 필드
4. **보상 트랜잭션 멱등성** - 중복 실행 가능하게
5. **@Async + 예외 처리** - 리스너 격리
6. **순서 의존성 제거** - 독립적인 리스너 설계

### 자주 하는 실수
1. @EventListener 사용 (→ @TransactionalEventListener)
2. 트랜잭션 내 외부 API 호출
3. 이벤트 리스너에서 예외 미처리
4. 보상 트랜잭션 멱등성 미보장
5. @Async 없이 긴 작업 수행

---

**작성일:** 2025-12-10
**버전:** 1.0
**출처:** Week 8 코치 Q&A (제이, 로이)
