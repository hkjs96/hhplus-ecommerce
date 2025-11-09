package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findById(String id);

    Coupon save(Coupon coupon);

    /**
     * ID로 Coupon을 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id Coupon ID
     * @return Coupon 엔티티
     * @throws BusinessException 쿠폰을 찾을 수 없을 때
     */
    default Coupon findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INVALID_COUPON,
                "쿠폰을 찾을 수 없습니다. couponId: " + id
            ));
    }
}
