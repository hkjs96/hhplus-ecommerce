package io.hhplus.ecommerce.application.coupon.dto;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;

import java.time.LocalDateTime;

public record UserCouponResponse(
    Long userCouponId,
    Long couponId,
    String couponName,
    Integer discountRate,
    String status,
    LocalDateTime issuedAt,
    LocalDateTime usedAt,
    LocalDateTime expiresAt
) {
    public static UserCouponResponse of(
            UserCoupon userCoupon,
            String couponName,
            Integer discountRate,
            LocalDateTime expiresAt
    ) {
        return new UserCouponResponse(
                userCoupon.getId(),
                userCoupon.getCouponId(),
                couponName,
                discountRate,
                userCoupon.getStatus().name(),
                userCoupon.getIssuedAt(),
                userCoupon.getUsedAt(),
                expiresAt
        );
    }
}
