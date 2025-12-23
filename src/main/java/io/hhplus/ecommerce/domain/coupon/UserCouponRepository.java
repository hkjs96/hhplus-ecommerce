package io.hhplus.ecommerce.domain.coupon;

import java.util.List;

public interface UserCouponRepository {

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    UserCoupon save(UserCoupon userCoupon);

    /**
     * 사용자 쿠폰 조회 (쿠폰 상세 정보 포함)
     * STEP 08 성능 최적화 Native Query - N+1 문제 해결
     */
    List<UserCouponProjection> findUserCouponsWithDetails(Long userId, String status);
}
