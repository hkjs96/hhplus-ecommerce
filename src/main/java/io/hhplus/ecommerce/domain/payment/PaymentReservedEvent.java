package io.hhplus.ecommerce.domain.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 결제 준비 완료 이벤트
 *
 * 발행 시점: 결제가 PENDING 상태로 DB에 저장된 직후 (잔액 차감 완료)
 *
 * 처리:
 * - PG API 호출 (비동기)
 * - 결제 결과에 따라 PaymentCompletedEvent 또는 PaymentFailedEvent 발행
 *
 * Phase 3 개선:
 * - PG API 호출을 비동기로 분리하여 응답 시간 대폭 단축 (2610ms → 50ms)
 * - DB 락 홀딩 시간 감소로 동시성 향상
 * - PG API 장애 격리 (부분 실패로 전환)
 */
@Getter
@AllArgsConstructor
public class PaymentReservedEvent {
    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final Long amount;
    private final String idempotencyKey;
}
