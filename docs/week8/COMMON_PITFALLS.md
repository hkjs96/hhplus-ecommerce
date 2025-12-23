# Week 8 자주 하는 실수 및 해결책

## 📋 목차
1. [이벤트 리스너 관련](#1-이벤트-리스너-관련)
2. [트랜잭션 관련](#2-트랜잭션-관련)
3. [비동기 처리 관련](#3-비동기-처리-관련)
4. [이벤트 설계 관련](#4-이벤트-설계-관련)
5. [보상 트랜잭션 관련](#5-보상-트랜잭션-관련)
6. [테스트 관련](#6-테스트-관련)

---

## 1. 이벤트 리스너 관련

### ❌ 실수 1-1: @EventListener 사용

**문제:**
```java
@Component
public class PaymentListener {
    @EventListener  // ❌ 잘못됨!
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        notificationService.sendPaymentConfirmation(event);
    }
}

@Service
public class PaymentUseCase {
    @Transactional
    public void processPayment() {
        Payment payment = executePayment();
        eventPublisher.publishEvent(new PaymentCompletedEvent(...));

        // 검증 실패로 롤백
        if (!validate(payment)) {
            throw new ValidationException();
        }
    }
}
```

**무엇이 문제인가?**
- `@EventListener`는 이벤트 발행 즉시 실행됨
- 트랜잭션 롤백되어도 알림이 발송됨
- 사용자는 "결제 완료" 알림을 받았지만 실제로는 결제 실패

**해결책:**
```java
@Component
public class PaymentListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)  // ✅ 올바름
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        notificationService.sendPaymentConfirmation(event);
    }
}
```

**핵심:**
> **@TransactionalEventListener는 필수!**
> - 트랜잭션 커밋 후에만 실행
> - 롤백 시 리스너 실행 안됨
> - 데이터 정합성 보장

---

### ❌ 실수 1-2: Phase 미지정

**문제:**
```java
@TransactionalEventListener  // phase 미지정
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    externalAPI.send(event);
}
```

**무엇이 문제인가?**
- 기본값은 `AFTER_COMMIT`이지만 명시적이지 않음
- 코드 리뷰어가 의도를 파악하기 어려움

**해결책:**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)  // ✅ 명시적
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    externalAPI.send(event);
}
```

---

### ❌ 실수 1-3: 예외 미처리

**문제:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    dataPlatformClient.send(event);      // 예외 발생 가능
    rankingService.update(event);        // 실행 안됨!
    notificationService.send(event);     // 실행 안됨!
}
```

**무엇이 문제인가?**
- 첫 번째 외부 API 실패 시 예외 전파
- 나머지 리스너들이 실행되지 않음

**해결책 1: @Async + try-catch**
```java
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    try {
        dataPlatformClient.send(event);
    } catch (Exception e) {
        log.error("데이터 플랫폼 전송 실패", e);
        // DLQ 또는 재시도 큐에 적재
    }
}
```

**해결책 2: 리스너 분리 + @Async**
```java
@Component
public class DataPlatformListener {
    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            dataPlatformClient.send(event);
        } catch (Exception e) {
            log.error("실패", e);
        }
    }
}

@Component
public class RankingListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        rankingService.update(event);
    }
}
```

---

### ❌ 실수 1-4: 순환 참조

**문제:**
```java
@Component
public class OrderListener {
    @TransactionalEventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 생성 → 결제 요청 이벤트 발행
        eventPublisher.publishEvent(new PaymentRequestedEvent(...));
    }
}

@Component
public class PaymentListener {
    @TransactionalEventListener
    public void handlePaymentRequested(PaymentRequestedEvent event) {
        // 결제 완료 → 주문 생성 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(...));  // ❌ 순환!
    }
}
```

**무엇이 문제인가?**
- 무한 루프 발생
- StackOverflowError
- 시스템 다운

**해결책:**
```java
// 이벤트 체이닝 재설계
@Component
public class OrderListener {
    @TransactionalEventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        eventPublisher.publishEvent(new PaymentRequestedEvent(...));
    }
}

@Component
public class PaymentListener {
    @TransactionalEventListener
    public void handlePaymentRequested(PaymentRequestedEvent event) {
        // 결제 처리 후 다른 이벤트 발행
        eventPublisher.publishEvent(new PaymentCompletedEvent(...));  // ✅
    }
}
```

---

## 2. 트랜잭션 관련

### ❌ 실수 2-1: 트랜잭션 내 외부 API 호출

**문제:**
```java
@Transactional
public void processPayment() {
    // DB 작업 (70ms)
    payment.execute();

    // 외부 API (3초) ❌
    dataPlatformClient.send(payment);
}
```

**무엇이 문제인가?**
- DB Connection을 3초간 점유
- Connection Pool 고갈
- TPS 급감

**해결책:**
```java
@Transactional
public void processPayment() {
    // DB 작업만 (70ms)
    payment.execute();

    // 이벤트 발행 (1ms)
    eventPublisher.publishEvent(new PaymentCompletedEvent(...));
}

@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 외부 API (3초) - 트랜잭션 밖에서 비동기 처리
    dataPlatformClient.send(event);
}
```

---

### ❌ 실수 2-2: 불필요하게 긴 트랜잭션

**문제:**
```java
@Transactional
public void processOrder() {
    // 1. 주문 생성 (필수)
    Order order = createOrder();

    // 2. 재고 차감 (필수)
    decreaseStock(order);

    // 3. 로깅 (불필요) ❌
    logService.log("주문 생성: " + order.getId());

    // 4. 통계 업데이트 (불필요) ❌
    statisticsService.update(order);

    // 5. 캐시 갱신 (불필요) ❌
    cacheService.evict("orders");
}
```

**무엇이 문제인가?**
- 로깅, 통계, 캐시는 트랜잭션 필요 없음
- 불필요한 트랜잭션 시간 증가

**해결책:**
```java
@Transactional
public void processOrder() {
    // ACID 필요한 작업만
    Order order = createOrder();
    decreaseStock(order);

    // 이벤트 발행
    eventPublisher.publishEvent(new OrderCreatedEvent(order));
}

@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) {
    logService.log("주문 생성: " + event.orderId());
    statisticsService.update(event);
    cacheService.evict("orders");
}
```

---

## 3. 비동기 처리 관련

### ❌ 실수 3-1: @Async 미설정

**문제:**
```java
@Configuration
@EnableAsync  // ✅ 있음
public class AsyncConfig { }

@Component
public class PaymentListener {
    @Async  // ❌ 동작 안함!
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        externalAPI.send(event);
    }
}
```

**무엇이 문제인가?**
- `@EnableAsync`만으로는 부족
- Thread Pool 설정 필요

**해결책:**
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
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

### ❌ 실수 3-2: 같은 클래스 내 @Async 호출

**문제:**
```java
@Component
public class PaymentService {
    public void processPayment() {
        Payment payment = executePayment();
        sendNotification(payment);  // ❌ 비동기 실행 안됨!
    }

    @Async
    public void sendNotification(Payment payment) {
        notificationService.send(payment);
    }
}
```

**무엇이 문제인가?**
- @Async는 프록시 기반
- 같은 클래스 내부 호출은 프록시를 거치지 않음
- 동기로 실행됨

**해결책:**
```java
@Component
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentNotificationService notificationService;

    public void processPayment() {
        Payment payment = executePayment();
        notificationService.sendNotification(payment);  // ✅ 다른 빈 호출
    }
}

@Component
public class PaymentNotificationService {
    @Async
    public void sendNotification(Payment payment) {
        notificationService.send(payment);
    }
}
```

---

### ❌ 실수 3-3: Thread Pool 크기 부적절

**문제:**
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);    // ❌ 너무 큼
        executor.setMaxPoolSize(200);     // ❌ 너무 큼
        executor.setQueueCapacity(10000); // ❌ 너무 큼
        return executor;
    }
}
```

**무엇이 문제인가?**
- 너무 큰 Thread Pool → 메모리 낭비, Context Switching 증가
- 너무 작은 Thread Pool → 대기 시간 증가

**해결책:**
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 적절한 크기 설정
        executor.setCorePoolSize(5);      // CPU 코어 수 or 동시 이벤트 처리 예상 수
        executor.setMaxPoolSize(10);      // 피크 시 예상 동시 요청 수
        executor.setQueueCapacity(100);   // 버스트 트래픽 대응

        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

## 4. 이벤트 설계 관련

### ❌ 실수 4-1: 가변 이벤트 객체

**문제:**
```java
@Getter @Setter  // ❌ Setter 있음
public class PaymentCompletedEvent {
    private Long paymentId;
    private BigDecimal amount;
}
```

**무엇이 문제인가?**
- 리스너에서 이벤트 내용 변경 가능
- 다른 리스너가 변경된 데이터 받음
- 예상치 못한 버그 발생

**해결책:**
```java
// Record 사용 (불변)
public record PaymentCompletedEvent(
    Long paymentId,
    BigDecimal amount
) {}

// 또는 final 필드 사용
@Getter
public class PaymentCompletedEvent {
    private final Long paymentId;
    private final BigDecimal amount;

    public PaymentCompletedEvent(Long paymentId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.amount = amount;
    }
}
```

---

### ❌ 실수 4-2: 이벤트 네이밍 불명확

**문제:**
```java
public record OrderEvent(Long orderId) {}  // ❌ 무슨 이벤트?
public record PaymentDone(Long paymentId) {}  // ❌ 과거형 아님
```

**해결책:**
```java
public record OrderCreatedEvent(Long orderId) {}  // ✅ 명확
public record OrderCancelledEvent(Long orderId) {}  // ✅ 명확
public record PaymentCompletedEvent(Long paymentId) {}  // ✅ 명확
```

**네이밍 규칙:**
- 과거형 사용 (Created, Completed, Cancelled)
- 도메인 용어 사용 (비즈니스 언어)
- Event 접미사 붙이기

---

### ❌ 실수 4-3: 이벤트에 너무 많은 정보

**문제:**
```java
public record PaymentCompletedEvent(
    Payment payment,              // ❌ Entity 전체
    Order order,                  // ❌ Entity 전체
    User user,                    // ❌ Entity 전체
    List<Product> products        // ❌ Entity 리스트
) {}
```

**무엇이 문제인가?**
- 이벤트가 너무 무거움
- 직렬화 문제 (나중에 Message Queue 사용 시)
- 불필요한 정보 노출

**해결책:**
```java
public record PaymentCompletedEvent(
    Long paymentId,               // ✅ ID만
    Long orderId,
    Long userId,
    BigDecimal amount,
    List<PaidProductInfo> products  // ✅ 필요한 정보만
) {
    public record PaidProductInfo(
        Long productId,
        int quantity
    ) {}
}
```

---

## 5. 보상 트랜잭션 관련

### ❌ 실수 5-1: 멱등성 미보장

**문제:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) {
    // 쿠폰 복구 (멱등하지 않음!)
    userCoupon.restore();  // ❌ 중복 실행 시 문제
}
```

**무엇이 문제인가?**
- 이벤트 중복 발행 시 여러 번 복구
- 데이터 정합성 깨짐

**해결책:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) {
    UserCoupon userCoupon = userCouponRepository.findById(event.getCouponId());

    // 멱등성 체크
    if (userCoupon.getStatus() != CouponStatus.USED) {
        log.info("쿠폰 이미 복구됨: {}", userCoupon.getId());
        return;
    }

    userCoupon.restore();
    userCouponRepository.save(userCoupon);
}
```

---

### ❌ 실수 5-2: 보상 트랜잭션 실패 미처리

**문제:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) {
    stockService.increaseStock(event.getProductId(), event.getQuantity());
    // 실패 시? ❌ 미처리
}
```

**무엇이 문제인가?**
- 보상 트랜잭션도 실패할 수 있음
- 실패 시 데이터 불일치

**해결책:**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) {
    try {
        stockService.increaseStock(event.getProductId(), event.getQuantity());
        log.info("재고 복구 완료: productId={}", event.getProductId());
    } catch (Exception e) {
        log.error("재고 복구 실패: productId={}", event.getProductId(), e);
        // Dead Letter Queue에 적재하여 수동 처리
        dlqService.enqueue("stock-compensation", event);
    }
}
```

---

## 6. 테스트 관련

### ❌ 실수 6-1: 비동기 테스트 대기 없음

**문제:**
```java
@Test
void 결제완료_이벤트_발행_시_알림_발송() {
    // given
    PaymentCompletedEvent event = new PaymentCompletedEvent(...);

    // when
    eventPublisher.publishEvent(event);

    // then (즉시 검증) ❌
    verify(notificationService).send(any());  // 실패!
}
```

**무엇이 문제인가?**
- @Async는 비동기 실행
- 테스트가 리스너 실행 전에 종료

**해결책:**
```java
@Test
void 결제완료_이벤트_발행_시_알림_발송() {
    // given
    PaymentCompletedEvent event = new PaymentCompletedEvent(...);

    // when
    eventPublisher.publishEvent(event);

    // then (비동기 대기)
    await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
        verify(notificationService).send(any());
    });
}
```

---

### ❌ 실수 6-2: 트랜잭션 롤백 테스트 누락

**문제:**
```java
// 성공 케이스만 테스트 ❌
@Test
void 결제_성공_시_이벤트_발행() {
    paymentService.processPayment(command);
    verify(eventPublisher).publishEvent(any());
}
```

**해결책:**
```java
// 롤백 케이스도 테스트 ✅
@Test
@Transactional
void 트랜잭션_롤백_시_이벤트_리스너_실행_안됨() {
    // when
    assertThatThrownBy(() -> {
        eventPublisher.publishEvent(new PaymentCompletedEvent(...));
        throw new RuntimeException("강제 롤백");
    });

    // then
    await().pollDelay(1, TimeUnit.SECONDS)
           .atMost(2, TimeUnit.SECONDS)
           .untilAsserted(() -> {
               verifyNoInteractions(notificationService);  // 리스너 실행 안됨
           });
}
```

---

## 📌 핵심 체크리스트

### 이벤트 리스너
- [ ] @TransactionalEventListener 사용
- [ ] phase = AFTER_COMMIT 명시
- [ ] 예외 처리 구현
- [ ] @Async + try-catch로 격리
- [ ] 순환 참조 방지

### 트랜잭션
- [ ] ACID 필요한 작업만 트랜잭션 내
- [ ] 외부 API는 AFTER_COMMIT
- [ ] 로깅/통계는 이벤트로 분리

### 비동기 처리
- [ ] AsyncConfig 설정
- [ ] 적절한 Thread Pool 크기
- [ ] 다른 빈으로 분리

### 이벤트 설계
- [ ] Record 사용 (불변)
- [ ] 과거형 네이밍
- [ ] 필요한 정보만 포함

### 보상 트랜잭션
- [ ] 멱등성 보장
- [ ] 실패 처리 (DLQ)
- [ ] 역순 보상

### 테스트
- [ ] 비동기 대기 (await)
- [ ] 트랜잭션 롤백 테스트
- [ ] 예외 케이스 테스트

---

## 🚨 긴급 디버깅

### 증상 1: 이벤트 리스너가 실행 안됨
**체크:**
1. @TransactionalEventListener 사용했는가?
2. 트랜잭션이 커밋되었는가?
3. phase가 올바른가?

### 증상 2: @Async가 동작 안함
**체크:**
1. @EnableAsync 설정했는가?
2. AsyncConfig 작성했는가?
3. 같은 클래스 내부 호출 아닌가?

### 증상 3: 트랜잭션이 롤백되어도 리스너 실행됨
**체크:**
1. @EventListener 사용한 건 아닌가?
2. @TransactionalEventListener 사용 확인

### 증상 4: 순환 참조 에러
**체크:**
1. 이벤트 체이닝이 순환하는가?
2. 이벤트 흐름도 그려보기

---

**작성일:** 2025-12-10
**버전:** 1.0
**참고:** [QNA_SUMMARY.md](./QNA_SUMMARY.md)
