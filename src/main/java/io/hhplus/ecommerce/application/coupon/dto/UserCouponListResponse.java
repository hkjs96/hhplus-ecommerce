package io.hhplus.ecommerce.application.coupon.dto;

import java.util.List;

public record UserCouponListResponse(
    Long userId,
    List<UserCouponResponse> coupons,
    Integer totalCount
) {
    public static UserCouponListResponse of(Long userId, List<UserCouponResponse> coupons) {
        return new UserCouponListResponse(userId, coupons, coupons.size());
    }
}
