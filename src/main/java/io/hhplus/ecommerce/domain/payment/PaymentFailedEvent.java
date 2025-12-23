package io.hhplus.ecommerce.domain.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 결제 실패 이벤트
 *
 * 발행 시점: PG API 호출 실패 또는 시스템 오류 발생
 *
 * 처리:
 * - 보상 트랜잭션 실행 (잔액 복구)
 * - 주문 상태 FAILED 업데이트
 * - 사용자 알림 (결제 실패 안내)
 *
 * Phase 4 개선:
 * - 실패 시나리오를 이벤트로 명확히 표현
 * - 보상 트랜잭션을 자동화
 * - 실패 추적 및 모니터링 용이
 */
@Getter
@AllArgsConstructor
public class PaymentFailedEvent {
    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final String failureReason;
    private final String idempotencyKey;
}
