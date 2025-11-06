package io.hhplus.ecommerce.application.coupon.dto;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserCouponResponse {
    private String userCouponId;
    private String couponId;
    private String couponName;
    private Integer discountRate;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;

    public static UserCouponResponse of(
            UserCoupon userCoupon,
            String couponName,
            Integer discountRate,
            LocalDateTime expiresAt
    ) {
        return UserCouponResponse.builder()
                .userCouponId(userCoupon.getId())
                .couponId(userCoupon.getCouponId())
                .couponName(couponName)
                .discountRate(discountRate)
                .status(userCoupon.getStatus().name())
                .issuedAt(userCoupon.getIssuedAt())
                .usedAt(userCoupon.getUsedAt())
                .expiresAt(expiresAt)
                .build();
    }
}
