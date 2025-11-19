package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.application.coupon.dto.UserCouponResponse;

import java.util.List;

public interface UserCouponRepository {

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    UserCoupon save(UserCoupon userCoupon);

    List<UserCouponProjection> findUserCouponsWithDetails(Long userId, String status);

    /**
     * 사용자 쿠폰 목록 조회 (DTO 변환 포함)
     * <p>
     * 코치 피드백 반영: Projection → DTO 변환을 Repository에서 수행
     * - UseCase 책임 감소
     * - 테스트 복잡도 감소
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 (null이면 전체 조회)
     * @return 쿠폰 DTO 목록
     */
    List<UserCouponResponse> findUserCouponsAsDto(Long userId, String status);
}
