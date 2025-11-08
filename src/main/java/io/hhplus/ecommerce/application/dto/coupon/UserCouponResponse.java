package io.hhplus.ecommerce.application.dto.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserCouponResponse {
    private String userCouponId;
    private String couponName;
    private Integer discountRate;
    private String status;
    private LocalDateTime expiresAt;
}
