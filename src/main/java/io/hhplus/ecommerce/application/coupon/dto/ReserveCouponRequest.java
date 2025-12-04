package io.hhplus.ecommerce.application.coupon.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 선착순 쿠폰 예약 요청
 */
public record ReserveCouponRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId
) {
}
