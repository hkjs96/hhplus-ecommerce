package io.hhplus.ecommerce.application.coupon.dto;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class IssueCouponResponse {
    private Long userCouponId;
    private Long couponId;
    private String couponName;
    private Integer discountRate;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Integer remainingQuantity;

    public static IssueCouponResponse of(
            UserCoupon userCoupon,
            String couponName,
            Integer discountRate,
            LocalDateTime expiresAt,
            Integer remainingQuantity
    ) {
        return IssueCouponResponse.builder()
                .userCouponId(userCoupon.getId())
                .couponId(userCoupon.getCouponId())
                .couponName(couponName)
                .discountRate(discountRate)
                .status(userCoupon.getStatus().name())
                .issuedAt(userCoupon.getIssuedAt())
                .expiresAt(expiresAt)
                .remainingQuantity(remainingQuantity)
                .build();
    }
}
