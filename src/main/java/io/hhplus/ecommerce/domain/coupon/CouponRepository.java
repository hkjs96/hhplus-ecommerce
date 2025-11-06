package io.hhplus.ecommerce.domain.coupon;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findById(String id);

    Coupon save(Coupon coupon);
}
