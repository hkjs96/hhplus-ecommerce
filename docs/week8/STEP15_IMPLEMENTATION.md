# Step 15: Application Event 구현 가이드

## 🎯 과제 목표

**Application Event를 활용하여 트랜잭션을 분리하고 도메인 간 결합도를 낮추는 것**

### Pass 조건
- [ ] ApplicationEventPublisher를 사용한 이벤트 발행
- [ ] @TransactionalEventListener를 사용한 이벤트 처리
- [ ] 최소 2개 이상의 도메인에 이벤트 적용
- [ ] 트랜잭션 경계가 명확히 분리됨
- [ ] 기존 기능이 정상 동작함 (회귀 테스트 통과)

---

## 📋 구현 단계

### Phase 1: 현재 코드 분석 (30분)

#### 1.1 긴 트랜잭션 찾기
**분석 대상 파일:**
- `PaymentUseCase.java`
- `OrderUseCase.java`
- `CouponUseCase.java`

**체크리스트:**
```markdown
### PaymentUseCase.processPayment()

**트랜잭션 범위:**
- [ ] 주문 조회 (DB 읽기)
- [ ] 잔액 차감 (DB 쓰기)
- [ ] 결제 생성 (DB 쓰기)
- [ ] 재고 차감 (DB 쓰기)
- [ ] 외부 데이터 플랫폼 전송 (HTTP API - 문제!)
- [ ] 랭킹 업데이트 (Redis - 문제!)

**예상 트랜잭션 시간:** 3-5초 (외부 API 포함)

**분리 대상:**
- 외부 데이터 플랫폼 전송
- 랭킹 업데이트
- 알림 발송 (있다면)
```

#### 1.2 의존성 파악
**Before (강결합):**
```java
@Service
@RequiredArgsConstructor
public class PaymentUseCase {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductService productService;
    private final DataPlatformClient dataPlatformClient;  // 외부 의존
    private final ProductRankingService rankingService;   // 외부 의존
    private final NotificationService notificationService; // 외부 의존

    @Transactional
    public PaymentResult processPayment(PaymentCommand command) {
        // 모든 의존성을 직접 호출
    }
}
```

**분리 목표:**
- PaymentUseCase의 의존성 3개 제거
- 이벤트 발행만 담당
- 각 리스너가 독립적으로 처리

---

### Phase 2: 이벤트 클래스 정의 (20분)

#### 2.1 이벤트 네이밍 규칙
- **과거형 사용**: `OrderCompletedEvent` (O), `OrderCompleteEvent` (X)
- **명확한 의미**: 이벤트 이름만 보고 무슨 일이 발생했는지 알 수 있어야 함
- **도메인 용어 사용**: 비즈니스 언어 반영

#### 2.2 이벤트 설계
**불변 객체로 설계 (Record 사용 권장):**
```java
package io.hhplus.ecommerce.application.payment.event;

/**
 * 결제 완료 이벤트
 *
 * 발행 시점: 결제 트랜잭션 커밋 후
 * 구독자: DataPlatformListener, RankingListener, NotificationListener
 */
public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    BigDecimal amount,
    List<PaidProductInfo> products,
    LocalDateTime completedAt
) {
    public record PaidProductInfo(
        Long productId,
        int quantity
    ) {}
}
```

**주요 이벤트 예시:**
```java
// 주문 관련
public record OrderCreatedEvent(Long orderId, Long userId, List<OrderItem> items) {}
public record OrderCancelledEvent(Long orderId, String reason) {}

// 재고 관련
public record StockDecreasedEvent(Long orderId, Long productId, int quantity) {}
public record StockDecreaseFailedEvent(Long orderId, String reason) {}

// 쿠폰 관련
public record CouponIssuedEvent(Long userCouponId, Long userId, Long couponId) {}
public record CouponUsedEvent(Long userCouponId, Long orderId) {}

// 결제 관련
public record PaymentCompletedEvent(...) {}
public record PaymentFailedEvent(Long orderId, String reason, Long couponId) {}
```

**이벤트 패키지 구조:**
```
application/
├── payment/
│   ├── dto/
│   ├── event/              # 이벤트 정의
│   │   ├── PaymentCompletedEvent.java
│   │   └── PaymentFailedEvent.java
│   ├── listener/           # 이벤트 리스너
│   │   ├── PaymentDataPlatformListener.java
│   │   ├── PaymentRankingListener.java
│   │   └── PaymentNotificationListener.java
│   └── usecase/
│       └── PaymentUseCase.java
```

---

### Phase 3: 이벤트 발행 (30분)

#### 3.1 ApplicationEventPublisher 주입
```java
@Service
@RequiredArgsConstructor
public class PaymentUseCase {
    private final ApplicationEventPublisher eventPublisher;
    // 외부 의존성 제거!
    // private final DataPlatformClient dataPlatformClient;
    // private final ProductRankingService rankingService;
    // private final NotificationService notificationService;
}
```

#### 3.2 트랜잭션 내 이벤트 발행
```java
@Transactional
public PaymentResult processPayment(PaymentCommand command) {
    // 1. 주문 조회
    Order order = orderRepository.findById(command.getOrderId())
        .orElseThrow(() -> new OrderNotFoundException(command.getOrderId()));

    // 2. 잔액 차감
    User user = userRepository.findById(command.getUserId())
        .orElseThrow(() -> new UserNotFoundException(command.getUserId()));
    user.deductBalance(order.getTotalAmount());

    // 3. 결제 생성
    Payment payment = Payment.create(order, user, command.getPaymentMethod());
    paymentRepository.save(payment);

    // 4. 재고 차감
    for (OrderItem item : order.getItems()) {
        Product product = productRepository.findById(item.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
        product.decreaseStock(item.getQuantity());
    }

    // 5. 이벤트 발행 (트랜잭션 커밋 후 처리됨)
    eventPublisher.publishEvent(new PaymentCompletedEvent(
        payment.getId(),
        order.getId(),
        user.getId(),
        payment.getAmount(),
        toPaidProductInfoList(order.getItems()),
        LocalDateTime.now()
    ));

    return PaymentResult.success(payment.getId());
}
// 트랜잭션 종료 (100ms 이하) - 외부 API 호출 제거로 50배 개선!
```

**개선 효과:**
- Before: 트랜잭션 5초 (외부 API 포함)
- After: 트랜잭션 100ms (핵심 로직만)
- TPS 향상: 2 → 100 (50배)

---

### Phase 4: 이벤트 리스너 구현 (40분)

#### 4.1 데이터 플랫폼 전송 리스너
```java
package io.hhplus.ecommerce.application.payment.listener;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentDataPlatformListener {
    private final DataPlatformClient dataPlatformClient;

    /**
     * 결제 완료 시 외부 데이터 플랫폼에 전송
     *
     * - AFTER_COMMIT: 트랜잭션 커밋 후 실행 (정합성 보장)
     * - @Async: 별도 스레드에서 비동기 처리 (격리)
     * - 재시도: 3회까지 시도
     * - 실패 시: DLQ에 적재하여 수동 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("데이터 플랫폼 전송 시작: paymentId={}", event.paymentId());

        try {
            dataPlatformClient.sendPaymentData(toDataPlatformDto(event));
            log.info("데이터 플랫폼 전송 성공: paymentId={}", event.paymentId());
        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 실패: paymentId={}", event.paymentId(), e);
            // 재시도 로직 (별도 메서드)
            retryWithBackoff(event, 0);
        }
    }

    private void retryWithBackoff(PaymentCompletedEvent event, int retryCount) {
        if (retryCount >= 3) {
            log.error("데이터 플랫폼 전송 최종 실패 - DLQ 적재: paymentId={}", event.paymentId());
            // Dead Letter Queue에 적재 (수동 처리)
            // dlqService.enqueue("data-platform", event);
            return;
        }

        try {
            // 지수 백오프 (1초, 2초, 4초)
            Thread.sleep((long) Math.pow(2, retryCount) * 1000);
            dataPlatformClient.sendPaymentData(toDataPlatformDto(event));
            log.info("데이터 플랫폼 전송 성공 (재시도 {}회): paymentId={}", retryCount + 1, event.paymentId());
        } catch (Exception e) {
            log.warn("데이터 플랫폼 전송 실패 (재시도 {}회): paymentId={}", retryCount + 1, event.paymentId());
            retryWithBackoff(event, retryCount + 1);
        }
    }

    private DataPlatformDto toDataPlatformDto(PaymentCompletedEvent event) {
        return DataPlatformDto.builder()
            .orderId(event.orderId())
            .userId(event.userId())
            .amount(event.amount())
            .completedAt(event.completedAt())
            .build();
    }
}
```

#### 4.2 랭킹 업데이트 리스너
```java
package io.hhplus.ecommerce.application.payment.listener;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRankingListener {
    private final ProductRankingService rankingService;

    /**
     * 결제 완료 시 상품 랭킹 업데이트
     *
     * - AFTER_COMMIT: 트랜잭션 커밋 후 실행
     * - 비동기 불필요: Redis 업데이트는 빠름 (10ms 이하)
     * - 실패해도 괜찮음: 랭킹은 참고용 데이터
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("랭킹 업데이트 시작: paymentId={}", event.paymentId());

        for (PaidProductInfo product : event.products()) {
            try {
                rankingService.incrementSalesCount(product.productId(), product.quantity());
                log.debug("랭킹 업데이트 성공: productId={}, quantity={}",
                          product.productId(), product.quantity());
            } catch (Exception e) {
                // 랭킹 업데이트 실패는 치명적이지 않음 (로그만)
                log.error("랭킹 업데이트 실패: productId={}", product.productId(), e);
            }
        }
    }
}
```

#### 4.3 알림 발송 리스너 (선택)
```java
package io.hhplus.ecommerce.application.payment.listener;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentNotificationListener {
    private final NotificationService notificationService;

    /**
     * 결제 완료 시 사용자에게 알림 발송
     *
     * - @Async: 사용자 응답 지연 방지
     * - 실패 시 재시도 없음: 알림은 중요도가 낮음
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            notificationService.sendPaymentConfirmation(
                event.userId(),
                event.orderId(),
                event.amount()
            );
            log.info("결제 완료 알림 발송 성공: userId={}, orderId={}",
                     event.userId(), event.orderId());
        } catch (Exception e) {
            log.error("결제 완료 알림 발송 실패: userId={}, orderId={}",
                      event.userId(), event.orderId(), e);
            // 알림 실패는 치명적이지 않음 (재시도 X)
        }
    }
}
```

---

### Phase 5: 비동기 설정 (20분)

#### 5.1 AsyncConfig 작성
```java
package io.hhplus.ecommerce.config;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Thread Pool 설정
        executor.setCorePoolSize(5);    // 기본 5개 스레드
        executor.setMaxPoolSize(10);    // 최대 10개 스레드
        executor.setQueueCapacity(100); // 대기 큐 100개

        // 스레드 이름 설정 (디버깅 용이)
        executor.setThreadNamePrefix("event-async-");

        // 거부 정책: 호출자의 스레드에서 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Graceful Shutdown
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("비동기 실행 중 예외 발생: method={}, params={}",
                      method.getName(), Arrays.toString(params), ex);
        };
    }
}
```

**설정 기준:**
- CorePoolSize: 예상 동시 이벤트 수 (5-10)
- MaxPoolSize: 피크 시 동시 요청 수 (10-20)
- QueueCapacity: 버스트 트래픽 대응 (100-200)

---

### Phase 6: 테스트 작성 (40분)

#### 6.1 단위 테스트
```java
@ExtendWith(MockitoExtension.class)
class PaymentUseCaseTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentUseCase paymentUseCase;

    @Test
    void 결제_성공_시_이벤트_발행() {
        // given
        PaymentCommand command = PaymentCommand.builder()
            .orderId(1L)
            .userId(1L)
            .amount(BigDecimal.valueOf(10000))
            .build();

        // when
        PaymentResult result = paymentUseCase.processPayment(command);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    }
}
```

#### 6.2 통합 테스트
```java
@SpringBootTest
class PaymentEventIntegrationTest {

    @Autowired
    private PaymentUseCase paymentUseCase;

    @MockBean
    private DataPlatformClient dataPlatformClient;

    @MockBean
    private ProductRankingService rankingService;

    @Test
    void 결제완료_이벤트_발행_및_리스너_처리() {
        // given
        PaymentCommand command = PaymentCommand.builder()
            .orderId(1L)
            .userId(1L)
            .amount(BigDecimal.valueOf(10000))
            .build();

        // when
        PaymentResult result = paymentUseCase.processPayment(command);

        // then
        assertThat(result.isSuccess()).isTrue();

        // 비동기 이벤트 처리 대기
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // 데이터 플랫폼 전송 확인
            verify(dataPlatformClient).sendPaymentData(any());

            // 랭킹 업데이트 확인
            verify(rankingService, atLeastOnce()).incrementSalesCount(anyLong(), anyInt());
        });
    }

    @Test
    @Transactional
    void 트랜잭션_롤백_시_이벤트_리스너_실행_안됨() {
        // given
        PaymentCommand command = PaymentCommand.builder()
            .orderId(999L) // 존재하지 않는 주문
            .userId(1L)
            .amount(BigDecimal.valueOf(10000))
            .build();

        // when & then
        assertThatThrownBy(() -> paymentUseCase.processPayment(command))
            .isInstanceOf(OrderNotFoundException.class);

        // 이벤트 리스너 실행 안됨
        await().pollDelay(1, TimeUnit.SECONDS)
               .atMost(2, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   verifyNoInteractions(dataPlatformClient);
                   verifyNoInteractions(rankingService);
               });
    }
}
```

#### 6.3 동시성 테스트
```java
@SpringBootTest
class PaymentConcurrencyTest {

    @Test
    void 동시_결제_시_이벤트_처리() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            executorService.submit(() -> {
                try {
                    PaymentCommand command = PaymentCommand.builder()
                        .orderId((long) userId)
                        .userId((long) userId)
                        .amount(BigDecimal.valueOf(10000))
                        .build();
                    paymentUseCase.processPayment(command);
                } finally {
                    latch.countDown();
                }
            });
        }

        // then
        latch.await(10, TimeUnit.SECONDS);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // 모든 결제에 대해 이벤트 처리 확인
            verify(dataPlatformClient, times(threadCount)).sendPaymentData(any());
        });
    }
}
```

---

## 📊 구현 체크리스트

### 필수 구현
- [ ] ApplicationEventPublisher 주입
- [ ] 최소 2개 이상의 이벤트 정의
- [ ] @TransactionalEventListener 사용
- [ ] AFTER_COMMIT phase 적용
- [ ] 트랜잭션 시간 측정 (Before/After 비교)

### 코드 품질
- [ ] 이벤트 클래스가 불변 객체 (Record)
- [ ] 이벤트 네이밍이 과거형
- [ ] 순환 참조 없음
- [ ] 예외 처리 구현
- [ ] 로깅 적절

### 테스트
- [ ] 단위 테스트 (이벤트 발행 확인)
- [ ] 통합 테스트 (리스너 실행 확인)
- [ ] 트랜잭션 롤백 테스트
- [ ] 비동기 처리 테스트

### 성능
- [ ] 트랜잭션 시간 50% 이상 감소
- [ ] 외부 API 호출이 트랜잭션 밖으로 분리됨
- [ ] Connection Pool 사용률 감소 확인

---

## 🚨 자주 하는 실수

### 1. @EventListener 사용
```java
// Bad
@EventListener
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 트랜잭션 롤백되어도 실행됨!
}

// Good
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    // 트랜잭션 커밋 후에만 실행
}
```

### 2. 가변 이벤트 객체
```java
// Bad
@Getter @Setter
public class PaymentCompletedEvent {
    private Long paymentId;
    // setter로 인한 의도치 않은 변경 가능
}

// Good
public record PaymentCompletedEvent(Long paymentId) {}
```

### 3. 예외 미처리
```java
// Bad
@TransactionalEventListener
public void handleEvent(PaymentCompletedEvent event) {
    externalAPI.call(); // 예외 발생 시 다른 리스너 실행 안됨
}

// Good
@Async
@TransactionalEventListener
public void handleEvent(PaymentCompletedEvent event) {
    try {
        externalAPI.call();
    } catch (Exception e) {
        log.error("Failed", e);
        // DLQ 또는 재시도
    }
}
```

### 4. 순환 참조
```java
// Bad
@TransactionalEventListener
public void handleOrderCreated(OrderCreatedEvent event) {
    eventPublisher.publishEvent(new PaymentRequestedEvent(...));
}

@TransactionalEventListener
public void handlePaymentRequested(PaymentRequestedEvent event) {
    eventPublisher.publishEvent(new OrderCreatedEvent(...)); // 순환!
}
```

---

## 💡 성능 측정

### Before (이벤트 분리 전)
```
평균 트랜잭션 시간: 3,500ms
TPS: 2.85 (10 connections)
Connection Pool 사용률: 95%
```

### After (이벤트 분리 후)
```
평균 트랜잭션 시간: 70ms (50배 개선)
TPS: 142.8 (10 connections) (50배 개선)
Connection Pool 사용률: 30% (65% 감소)
```

---

## 📚 참고 자료

- [Spring Event Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [QNA_SUMMARY.md](./QNA_SUMMARY.md) - Q2: Application Event 활용
- [COMMON_PITFALLS.md](./COMMON_PITFALLS.md) - 자주 하는 실수

---

**작성일:** 2025-12-10
**버전:** 1.0
