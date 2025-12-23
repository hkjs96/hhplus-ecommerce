package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.application.usecase.order.PaymentIdempotencyService;
import io.hhplus.ecommerce.application.usecase.order.PaymentTransactionService;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.payment.PaymentFailedEvent;
import io.hhplus.ecommerce.domain.payment.PaymentReservedEvent;
import io.hhplus.ecommerce.infrastructure.external.PGResponse;
import io.hhplus.ecommerce.infrastructure.external.PGService;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@RecordApplicationEvents
class PgApiEventHandlerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PGService pgService;

    @MockitoBean
    private PaymentTransactionService transactionService;

    @MockitoBean
    private PaymentIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        // Setup은 각 테스트에서 필요시 수행
    }

    @Test
    @DisplayName("PG API 성공 - PaymentCompletedEvent 발행")
    void shouldHandleSuccessfulPgApiCall() throws InterruptedException {
        // given
        Long paymentId = 1L;
        Long orderId = 100L;
        Long userId = 10L;
        Long amount = 50000L;
        String idempotencyKey = "payment-key-001";
        String txId = "TOSS_TX_12345";

        PaymentReservedEvent event = new PaymentReservedEvent(paymentId, orderId, userId, amount, idempotencyKey);

        // PG API 성공 응답
        given(pgService.charge(any(PaymentRequest.class)))
            .willReturn(PGResponse.success(txId));

        // PaymentResponse mock
        PaymentResponse paymentResponse = PaymentResponse.of(
            orderId,
            amount,
            10000L,  // remainingBalance
            "SUCCESS",
            "SUCCESS",
            LocalDateTime.now()
        );
        given(transactionService.updatePaymentSuccessAndCreateResponse(orderId, userId, txId))
            .willReturn(paymentResponse);

        // Order mock
        Order mockOrder = mock(Order.class);
        given(transactionService.getOrder(orderId))
            .willReturn(mockOrder);

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // PaymentCompletedEvent가 발행되었는지 확인
        long completedEventCount = applicationEvents.stream(io.hhplus.ecommerce.domain.order.PaymentCompletedEvent.class).count();
        assertThat(completedEventCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("PG API 승인 실패 - PaymentFailedEvent 발행")
    void shouldHandlePgApiFailure() throws InterruptedException {
        // given
        Long paymentId = 2L;
        Long orderId = 200L;
        Long userId = 20L;
        Long amount = 30000L;
        String idempotencyKey = "payment-key-002";

        PaymentReservedEvent event = new PaymentReservedEvent(paymentId, orderId, userId, amount, idempotencyKey);

        // PG API 실패 응답
        given(pgService.charge(any(PaymentRequest.class)))
            .willReturn(PGResponse.failure("잔액 부족"));

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // PaymentFailedEvent가 발행되었는지 확인
        long failedEventCount = applicationEvents.stream(PaymentFailedEvent.class).count();
        assertThat(failedEventCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("PG API 호출 예외 - PaymentFailedEvent 발행")
    void shouldHandlePgApiException() throws InterruptedException {
        // given
        Long paymentId = 3L;
        Long orderId = 300L;
        Long userId = 30L;
        Long amount = 40000L;
        String idempotencyKey = "payment-key-003";

        PaymentReservedEvent event = new PaymentReservedEvent(paymentId, orderId, userId, amount, idempotencyKey);

        // PG API 호출 시 예외 발생
        given(pgService.charge(any(PaymentRequest.class)))
            .willThrow(new RuntimeException("네트워크 타임아웃"));

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // PaymentFailedEvent가 발행되었는지 확인
        long failedEventCount = applicationEvents.stream(PaymentFailedEvent.class).count();
        assertThat(failedEventCount).isGreaterThan(0);
    }
}
