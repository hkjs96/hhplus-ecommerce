package io.hhplus.ecommerce.application.coupon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 예약 응답
 */
public record ReserveCouponResponse(
    Long reservationId,
    Long couponId,
    Long userId,
    Long sequenceNumber,
    String status,
    String message,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime reservedAt
) {
    public static ReserveCouponResponse of(
        Long reservationId,
        Long couponId,
        Long userId,
        Long sequenceNumber,
        LocalDateTime reservedAt
    ) {
        return new ReserveCouponResponse(
            reservationId,
            couponId,
            userId,
            sequenceNumber,
            "RESERVED",
            String.format("쿠폰 발급 예약이 완료되었습니다. (%d번째)", sequenceNumber),
            reservedAt
        );
    }
}
