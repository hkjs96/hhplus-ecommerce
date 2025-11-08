package io.hhplus.ecommerce.application.dto.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class IssueCouponResponse {
    private String userCouponId;
    private String couponName;
    private Integer discountRate;
    private LocalDateTime expiresAt;
    private Integer remainingQuantity;
}
