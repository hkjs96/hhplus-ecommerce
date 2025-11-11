package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findById(Long id);

    Optional<Coupon> findByCouponCode(String couponCode);

    Coupon save(Coupon coupon);

    /**
     * ID로 Coupon을 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id Coupon ID (BIGINT)
     * @return Coupon 엔티티
     * @throws BusinessException 쿠폰을 찾을 수 없을 때
     */
    default Coupon findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_COUPON,
                "쿠폰을 찾을 수 없습니다. couponId: " + id
            ));
    }

    /**
     * 쿠폰 코드(Business ID)로 Coupon을 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param couponCode Coupon Code (e.g., "COUPON-2025-001")
     * @return Coupon 엔티티
     * @throws BusinessException 쿠폰을 찾을 수 없을 때
     */
    default Coupon findByCouponCodeOrThrow(String couponCode) {
        return findByCouponCode(couponCode)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_COUPON,
                "쿠폰을 찾을 수 없습니다. couponCode: " + couponCode
            ));
    }
}
