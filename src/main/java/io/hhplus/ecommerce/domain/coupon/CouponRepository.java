package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findById(Long id);

    Optional<Coupon> findByCouponCode(String couponCode);

    Coupon save(Coupon coupon);

    default Coupon findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_COUPON,
                "쿠폰을 찾을 수 없습니다. couponId: " + id
            ));
    }

    default Coupon findByCouponCodeOrThrow(String couponCode) {
        return findByCouponCode(couponCode)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_COUPON,
                "쿠폰을 찾을 수 없습니다. couponCode: " + couponCode
            ));
    }
}
