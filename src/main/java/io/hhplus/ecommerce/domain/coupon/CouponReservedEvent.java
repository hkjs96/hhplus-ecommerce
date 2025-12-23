package io.hhplus.ecommerce.domain.coupon;

import lombok.Getter;

/**
 * 쿠폰 예약 완료 이벤트
 *
 * 선착순 자격 획득 후 발행되는 이벤트
 * - TransactionalEventListener(AFTER_COMMIT)에서 수신
 * - 실제 쿠폰 발급 처리 트리거
 */
@Getter
public class CouponReservedEvent {

    private final Long couponId;
    private final Long userId;
    private final Long sequenceNumber;

    public CouponReservedEvent(
        Long couponId,
        Long userId,
        Long sequenceNumber
    ) {
        this.couponId = couponId;
        this.userId = userId;
        this.sequenceNumber = sequenceNumber;
    }
}
