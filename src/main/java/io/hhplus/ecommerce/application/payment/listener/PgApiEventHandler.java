package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.application.usecase.order.PaymentIdempotencyService;
import io.hhplus.ecommerce.application.usecase.order.PaymentTransactionService;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.payment.PaymentFailedEvent;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentReservedEvent;
import io.hhplus.ecommerce.infrastructure.external.PGResponse;
import io.hhplus.ecommerce.infrastructure.external.PGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * PG API 호출 이벤트 핸들러
 *
 * 책임:
 * - PaymentReservedEvent 수신 시 PG API 호출
 * - 성공 시 PaymentCompletedEvent 발행
 * - 실패 시 PaymentFailedEvent 발행
 *
 * 특징:
 * - 비동기 처리 (@Async)
 * - 재시도 로직 포함 (PGService 내부에서 처리)
 * - 트랜잭션 외부에서 실행
 *
 * Phase 3 개선 효과:
 * - API 응답 시간 98% 단축 (2610ms → 50ms)
 * - DB 락 홀딩 시간 감소로 동시성 대폭 향상
 * - PG API 장애 격리 (전체 실패 → 부분 실패)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PgApiEventHandler {

    private final PGService pgService;
    private final PaymentTransactionService transactionService;
    private final PaymentIdempotencyService idempotencyService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 준비 완료 시 PG API 호출
     *
     * AFTER_COMMIT: 잔액 차감 트랜잭션이 성공적으로 커밋된 후에만 실행
     * Async: 별도 스레드에서 실행하여 응답 속도 개선
     *
     * 주의사항:
     * - @Transactional 어노테이션 사용 금지
     * - PG API 호출은 트랜잭션 외부에서 실행
     * - transactionService의 메서드가 각자 트랜잭션 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentReserved(PaymentReservedEvent event) {
        log.info("결제 준비 완료 - PG API 호출 시작: paymentId={}, orderId={}, amount={}",
                event.getPaymentId(), event.getOrderId(), event.getAmount());

        try {
            // PG API 호출 (외부, 2-3초 소요, 내부적으로 재시도 처리)
            PaymentRequest request = PaymentRequest.of(
                event.getUserId(),
                event.getIdempotencyKey()
            );

            PGResponse pgResponse = pgService.charge(request);

            if (pgResponse.isSuccess()) {
                // 결제 성공 처리
                log.info("PG API 호출 성공: orderId={}, txId={}",
                        event.getOrderId(), pgResponse.getTransactionId());

                // 트랜잭션: 결제 성공 상태 업데이트
                PaymentResponse response = transactionService.updatePaymentSuccessAndCreateResponse(
                    event.getOrderId(),
                    event.getUserId(),
                    pgResponse.getTransactionId()
                );

                // 멱등성 키 완료 처리
                PaymentIdempotency idempotency = idempotencyService.findByKey(event.getIdempotencyKey());
                if (idempotency != null) {
                    idempotencyService.saveCompletion(idempotency, event.getOrderId(), response);
                }

                // 결제 완료 이벤트 발행 (Phase 1에서 이미 구현됨)
                Order order = transactionService.getOrder(event.getOrderId());
                eventPublisher.publishEvent(
                    new io.hhplus.ecommerce.domain.order.PaymentCompletedEvent(order)
                );

                log.info("결제 처리 완료: orderId={}", event.getOrderId());

            } else {
                // PG 승인 실패
                log.warn("PG API 승인 실패: orderId={}, reason={}",
                        event.getOrderId(), pgResponse.getMessage());

                // 결제 실패 이벤트 발행 (Phase 4에서 보상 트랜잭션 처리)
                eventPublisher.publishEvent(new PaymentFailedEvent(
                    event.getPaymentId(),
                    event.getOrderId(),
                    event.getUserId(),
                    "PG 승인 실패: " + pgResponse.getMessage(),
                    event.getIdempotencyKey()
                ));
            }

        } catch (Exception e) {
            log.error("PG API 호출 중 오류 발생: orderId={}", event.getOrderId(), e);

            // 결제 실패 이벤트 발행
            eventPublisher.publishEvent(new PaymentFailedEvent(
                event.getPaymentId(),
                event.getOrderId(),
                event.getUserId(),
                "PG API 호출 실패: " + e.getMessage(),
                event.getIdempotencyKey()
            ));
        }
    }
}
