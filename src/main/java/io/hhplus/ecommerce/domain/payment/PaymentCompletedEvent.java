package io.hhplus.ecommerce.domain.payment;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 결제 완료 이벤트
 * <p>
 * DB 트랜잭션 커밋 후 발행되어 외부 API 호출 트리거
 * - @TransactionalEventListener(phase = AFTER_COMMIT)에서 수신
 * - 외부 API 실패해도 결제는 완료 상태 유지
 * <p>
 * 5명 페르소나 분석:
 * - 김데이터(O): DB 트랜잭션 최소화, 외부 API는 트랜잭션 밖
 * - 박트래픽(△): 비동기 처리 권장하지만 동기로 시작 가능
 * - 이금융(△): 외부 API 실패 시 보상 트랜잭션 필요 (추후 구현)
 * - 최아키텍트(O): Event-driven 아키텍처
 * - 정스타트업(O): @TransactionalEventListener로 간단하게
 * <p>
 * 최종 선택: Spring Events + @TransactionalEventListener(AFTER_COMMIT)
 */
@Getter
public class PaymentCompletedEvent {

    private final Long orderId;
    private final Long userId;
    private final Long paidAmount;
    private final LocalDateTime paidAt;

    public PaymentCompletedEvent(Long orderId, Long userId, Long paidAmount, LocalDateTime paidAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.paidAmount = paidAmount;
        this.paidAt = paidAt;
    }

    public static PaymentCompletedEvent of(Long orderId, Long userId, Long paidAmount, LocalDateTime paidAt) {
        return new PaymentCompletedEvent(orderId, userId, paidAmount, paidAt);
    }
}
