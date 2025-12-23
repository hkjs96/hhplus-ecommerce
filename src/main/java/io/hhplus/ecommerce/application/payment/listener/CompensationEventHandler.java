package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.application.usecase.order.PaymentIdempotencyService;
import io.hhplus.ecommerce.application.usecase.order.PaymentTransactionService;
import io.hhplus.ecommerce.domain.payment.PaymentFailedEvent;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * 보상 트랜잭션 핸들러
 *
 * 책임:
 * - 실패 이벤트 수신 시 롤백 처리
 * - 데이터 정합성 보장
 * - 재시도 가능 상태로 복구
 *
 * Phase 4 개선 효과:
 * - 실패 처리를 자동화하여 수동 개입 최소화
 * - 보상 트랜잭션을 명확한 이벤트 흐름으로 표현
 * - 실패 추적 및 모니터링 용이
 *
 * 처리 시나리오:
 * 1. PaymentFailedEvent 수신
 * 2. 잔액 복구 (보상 트랜잭션)
 * 3. 주문 상태 FAILED 업데이트
 * 4. 멱등성 키 FAILED 상태로 업데이트
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CompensationEventHandler {

    private final PaymentTransactionService transactionService;
    private final PaymentIdempotencyService idempotencyService;

    /**
     * 결제 실패 시 보상 트랜잭션 실행
     *
     * 실행 순서:
     * 1. 잔액 복구 (차감된 금액 환불)
     * 2. 주문 상태 FAILED 업데이트
     * 3. 멱등성 키 FAILED 상태로 업데이트
     *
     * AFTER_COMMIT: 실패 이벤트 발행 트랜잭션 커밋 후 실행
     * Async: 별도 스레드에서 실행
     *
     * 주의사항:
     * - @Transactional 어노테이션 사용 금지
     * - transactionService.compensatePayment()가 내부적으로 트랜잭션 처리
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailure(PaymentFailedEvent event) {
        log.error("결제 실패 - 보상 트랜잭션 시작: orderId={}, paymentId={}, reason={}",
                event.getOrderId(), event.getPaymentId(), event.getFailureReason());

        try {
            // 1. 잔액 복구 (보상 트랜잭션)
            transactionService.compensatePayment(event.getOrderId(), event.getUserId());
            log.info("잔액 복구 완료: orderId={}, userId={}", event.getOrderId(), event.getUserId());

            // 2. 멱등성 키 실패 처리
            PaymentIdempotency idempotency = idempotencyService.findByKey(event.getIdempotencyKey());
            if (idempotency != null && !idempotency.isFailed()) {
                idempotencyService.saveFailure(idempotency, event.getFailureReason());
                log.info("멱등성 키 실패 처리 완료: idempotencyKey={}", event.getIdempotencyKey());
            }

            log.info("보상 트랜잭션 완료: orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("보상 트랜잭션 실패: orderId={}, paymentId={}. Manual intervention required!",
                    event.getOrderId(), event.getPaymentId(), e);

            // 보상 트랜잭션 실패 시 Dead Letter Queue에 적재 (향후 구현)
            // 현재는 로그만 남기고 수동 처리 필요
        }
    }
}
