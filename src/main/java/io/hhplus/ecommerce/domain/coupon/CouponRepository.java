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

    /**
     * Pessimistic Lock을 사용한 쿠폰 조회 (SELECT FOR UPDATE)
     * - 동시성 제어: 쿠폰 발급 시 사용
     * - 선착순 이벤트에서 정확한 수량 제어 보장
     */
    Optional<Coupon> findByIdWithLock(Long id);

    default Coupon findByIdWithLockOrThrow(Long id) {
        return findByIdWithLock(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_COUPON,
                "쿠폰을 찾을 수 없습니다. couponId: " + id
            ));
    }
}
