package io.hhplus.ecommerce.application.dto.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserCouponsResponse {
    private List<UserCouponResponse> coupons;
}
