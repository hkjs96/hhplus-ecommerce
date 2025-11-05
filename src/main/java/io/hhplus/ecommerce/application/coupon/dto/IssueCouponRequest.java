package io.hhplus.ecommerce.application.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 발급 요청 DTO
 * - POST /coupons/{couponId}/issue
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponRequest {
    private String userId;
}
