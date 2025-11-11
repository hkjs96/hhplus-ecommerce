package io.hhplus.ecommerce.domain.coupon;

import java.util.List;

public interface UserCouponRepository {

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    UserCoupon save(UserCoupon userCoupon);
}
