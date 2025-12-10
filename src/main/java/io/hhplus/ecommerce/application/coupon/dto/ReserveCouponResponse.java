package io.hhplus.ecommerce.application.coupon.dto;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 예약 응답
 */
public record ReserveCouponResponse(
    Long couponId,
    Long userId,
    Long sequenceNumber,
    String status,
    String message
) {
    public static ReserveCouponResponse of(
        Long couponId,
        Long userId,
        Long sequenceNumber
    ) {
        return new ReserveCouponResponse(
            couponId,
            userId,
            sequenceNumber,
            "RESERVED",
            String.format("쿠폰 발급 예약이 완료되었습니다. (%d번째)", sequenceNumber)
        );
    }
}
