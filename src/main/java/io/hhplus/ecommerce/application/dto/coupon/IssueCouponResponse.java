package io.hhplus.ecommerce.application.dto.coupon;

import java.time.LocalDateTime;

public record IssueCouponResponse(
    String userCouponId,
    String couponName,
    Integer discountRate,
    LocalDateTime expiresAt,
    Integer remainingQuantity
) {
}
