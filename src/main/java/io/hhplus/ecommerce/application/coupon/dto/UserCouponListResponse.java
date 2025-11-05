package io.hhplus.ecommerce.application.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 보유 쿠폰 목록 응답 DTO
 * - GET /users/{userId}/coupons
 */
@Getter
@Builder
@AllArgsConstructor
public class UserCouponListResponse {
    private String userId;
    private List<UserCouponResponse> coupons;
    private Integer totalCount;

    public static UserCouponListResponse of(String userId, List<UserCouponResponse> coupons) {
        return UserCouponListResponse.builder()
                .userId(userId)
                .coupons(coupons)
                .totalCount(coupons.size())
                .build();
    }
}
