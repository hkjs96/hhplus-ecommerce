package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.infrastructure.redis.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이벤트 멱등성 체크 전용 리스너
 *
 * 책임: 이벤트 중복 처리 방지 (Single Responsibility)
 * - 멱등성 체크
 * - 처리 완료 기록
 *
 * 실행 순서: @Order(1) - 가장 먼저 실행
 * - 다른 리스너보다 먼저 실행되어 중복 이벤트 차단
 * - 중복 이벤트 발견 시 예외를 던져 후속 리스너 실행 방지
 *
 * 8주차 코치 피드백 반영:
 * - "리스너 1개 = 책임 1개"
 * - "예외를 던져야 재시도 작동"
 */
// @Component  // 비활성화: 각 리스너가 독립적으로 멱등성 관리
@Order(1)  // 가장 먼저 실행
@RequiredArgsConstructor
@Slf4j
public class EventIdempotencyListener {

    private final EventIdempotencyService idempotencyService;

    /**
     * 멱등성 체크 및 기록 (원자적 선점 방식)
     *
     * @param event PaymentCompletedEvent
     * @throws DuplicateEventException 중복 이벤트인 경우
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void checkIdempotency(PaymentCompletedEvent event) {
        String eventType = "PaymentCompleted";
        String eventId = "order-" + event.getOrder().getId();

        // 1. 원자적 선점 시도 (SET NX)
        // 성공하면 최초 처리, 실패하면 중복 이벤트
        boolean isFirstProcessing = idempotencyService.markAsProcessed(eventType, eventId);

        if (!isFirstProcessing) {
            // 2. 중복 이벤트인 경우 예외를 던져 후속 리스너 실행 방지
            log.info("중복 이벤트 감지, 처리 중단: eventId={}", eventId);
            throw new DuplicateEventException("이미 처리된 이벤트입니다: " + eventId);
        }

        // 3. 최초 처리인 경우 기록 완료
        log.debug("멱등성 기록 완료: eventId={}", eventId);
    }

    /**
     * 중복 이벤트 예외
     */
    public static class DuplicateEventException extends RuntimeException {
        public DuplicateEventException(String message) {
            super(message);
        }
    }
}
