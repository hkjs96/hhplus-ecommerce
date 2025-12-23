package io.hhplus.ecommerce.domain.coupon;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 + 쿠폰 정보 조회 Native Query Projection
 *
 * 용도: GetUserCouponsUseCase에서 사용
 * 쿼리: Native Query로 user_coupons + coupons JOIN 결과 매핑
 */
public interface UserCouponProjection {

    // UserCoupon 정보
    Long getUserCouponId();

    Long getUserId();

    Long getCouponId();

    String getStatus();

    LocalDateTime getIssuedAt();

    LocalDateTime getUsedAt();

    // Coupon 정보
    String getCouponName();

    Integer getDiscountRate();

    LocalDateTime getExpiresAt();
}
