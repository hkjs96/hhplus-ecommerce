package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.application.usecase.order.PaymentIdempotencyService;
import io.hhplus.ecommerce.application.usecase.order.PaymentTransactionService;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.payment.PaymentFailedEvent;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class CompensationEventHandlerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PaymentTransactionService transactionService;

    @MockitoBean
    private PaymentIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        // Setup은 각 테스트에서 필요시 수행
    }

    @Test
    @DisplayName("정상 보상 트랜잭션 - 잔액 복구 및 멱등성 실패 처리")
    void shouldCompensateSuccessfully() throws InterruptedException {
        // given
        Long paymentId = 1L;
        Long orderId = 100L;
        Long userId = 10L;
        String failureReason = "PG API 호출 실패";
        String idempotencyKey = "payment-key-001";

        PaymentFailedEvent event = new PaymentFailedEvent(paymentId, orderId, userId, failureReason, idempotencyKey);

        // PaymentIdempotency mock
        PaymentIdempotency mockIdempotency = mock(PaymentIdempotency.class);
        given(mockIdempotency.isFailed()).willReturn(false);
        given(idempotencyService.findByKey(idempotencyKey))
            .willReturn(mockIdempotency);

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // compensatePayment 호출 확인
        verify(transactionService, times(1)).compensatePayment(orderId, userId);

        // 멱등성 키 실패 처리 확인
        verify(idempotencyService, times(1)).saveFailure(mockIdempotency, failureReason);
    }

    @Test
    @DisplayName("멱등성 키가 이미 FAILED 상태 - 중복 처리 방지")
    void shouldSkipIfAlreadyFailed() throws InterruptedException {
        // given
        Long paymentId = 2L;
        Long orderId = 200L;
        Long userId = 20L;
        String failureReason = "PG API 승인 실패";
        String idempotencyKey = "payment-key-002";

        PaymentFailedEvent event = new PaymentFailedEvent(paymentId, orderId, userId, failureReason, idempotencyKey);

        // PaymentIdempotency mock (이미 FAILED 상태)
        PaymentIdempotency mockIdempotency = mock(PaymentIdempotency.class);
        given(mockIdempotency.isFailed()).willReturn(true);
        given(idempotencyService.findByKey(idempotencyKey))
            .willReturn(mockIdempotency);

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // compensatePayment는 호출되지만, saveFailure는 호출되지 않아야 함
        verify(transactionService, times(1)).compensatePayment(orderId, userId);
        verify(idempotencyService, never()).saveFailure(any(), anyString());
    }

    @Test
    @DisplayName("보상 트랜잭션 예외 발생 - 로그만 남기고 예외 전파하지 않음")
    void shouldHandleCompensationException() throws InterruptedException {
        // given
        Long paymentId = 3L;
        Long orderId = 300L;
        Long userId = 30L;
        String failureReason = "네트워크 타임아웃";
        String idempotencyKey = "payment-key-003";

        PaymentFailedEvent event = new PaymentFailedEvent(paymentId, orderId, userId, failureReason, idempotencyKey);

        // compensatePayment 예외 발생
        doThrow(new RuntimeException("DB 연결 실패"))
            .when(transactionService).compensatePayment(orderId, userId);

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // compensatePayment 호출 확인
        verify(transactionService, times(1)).compensatePayment(orderId, userId);

        // 예외 발생으로 멱등성 키 처리는 수행되지 않음
        verify(idempotencyService, never()).findByKey(anyString());
    }

    @Test
    @DisplayName("멱등성 키가 null인 경우 - 보상 트랜잭션만 실행")
    void shouldCompensateWithoutIdempotency() throws InterruptedException {
        // given
        Long paymentId = 4L;
        Long orderId = 400L;
        Long userId = 40L;
        String failureReason = "PG API 오류";
        String idempotencyKey = "payment-key-004";

        PaymentFailedEvent event = new PaymentFailedEvent(paymentId, orderId, userId, failureReason, idempotencyKey);

        // findByKey가 null 반환
        given(idempotencyService.findByKey(idempotencyKey))
            .willReturn(null);

        // when - 트랜잭션 내에서 이벤트 발행
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        // @Async + @TransactionalEventListener(AFTER_COMMIT) 대기
        Thread.sleep(1000);

        // then
        // compensatePayment는 호출되지만, saveFailure는 호출되지 않음
        verify(transactionService, times(1)).compensatePayment(orderId, userId);
        verify(idempotencyService, never()).saveFailure(any(), anyString());
    }
}
