package io.hhplus.ecommerce.domain.coupon;

import java.time.LocalDateTime;

public interface UserCouponProjection {

    // UserCoupon 정보
    Long getUserCouponId();

    Long getUserId();

    Long getCouponId();

    String getStatus();

    LocalDateTime getIssuedAt();

    LocalDateTime getUsedAt();

    // Coupon 정보
    String getCouponName();

    Integer getDiscountRate();

    LocalDateTime getExpiresAt();
}
