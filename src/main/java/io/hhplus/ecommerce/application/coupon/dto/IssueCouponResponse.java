package io.hhplus.ecommerce.application.coupon.dto;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;

import java.time.LocalDateTime;

public record IssueCouponResponse(
    Long userCouponId,
    Long couponId,
    String couponName,
    Integer discountRate,
    String status,
    LocalDateTime issuedAt,
    LocalDateTime expiresAt,
    Integer remainingQuantity
) {
    public static IssueCouponResponse of(
            UserCoupon userCoupon,
            String couponName,
            Integer discountRate,
            LocalDateTime expiresAt,
            Integer remainingQuantity
    ) {
        return new IssueCouponResponse(
                userCoupon.getId(),
                userCoupon.getCouponId(),
                couponName,
                discountRate,
                userCoupon.getStatus().name(),
                userCoupon.getIssuedAt(),
                expiresAt,
                remainingQuantity
        );
    }
}
