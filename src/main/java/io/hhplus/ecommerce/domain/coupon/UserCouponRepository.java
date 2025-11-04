package io.hhplus.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 쿠폰 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface UserCouponRepository {

    /**
     * 사용자 쿠폰 ID로 조회
     */
    Optional<UserCoupon> findById(String id);

    /**
     * 사용자 ID로 쿠폰 목록 조회
     */
    List<UserCoupon> findByUserId(String userId);

    /**
     * 사용자 ID와 상태로 쿠폰 목록 조회
     */
    List<UserCoupon> findByUserIdAndStatus(String userId, CouponStatus status);

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인
     */
    boolean existsByUserIdAndCouponId(String userId, String couponId);

    /**
     * 모든 사용자 쿠폰 조회
     */
    List<UserCoupon> findAll();

    /**
     * 사용자 쿠폰 저장 (생성 및 업데이트)
     */
    UserCoupon save(UserCoupon userCoupon);

    /**
     * 사용자 쿠폰 삭제
     */
    void deleteById(String id);

    /**
     * 사용자 쿠폰 존재 여부 확인
     */
    boolean existsById(String id);
}
