package io.hhplus.ecommerce.domain.coupon;

import java.util.List;

public interface UserCouponRepository {

    List<UserCoupon> findByUserId(String userId);

    boolean existsByUserIdAndCouponId(String userId, String couponId);

    UserCoupon save(UserCoupon userCoupon);
}
