package io.hhplus.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 예약 Repository 인터페이스
 */
public interface CouponReservationRepository {

    /**
     * 예약 저장
     */
    CouponReservation save(CouponReservation reservation);

    /**
     * ID로 조회
     */
    Optional<CouponReservation> findById(Long id);

    /**
     * 사용자 ID와 쿠폰 ID로 조회
     */
    Optional<CouponReservation> findByUserIdAndCouponId(Long userId, Long couponId);

    /**
     * 중복 예약 체크
     * @return true: 이미 예약함, false: 예약 안 함
     */
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    /**
     * 특정 쿠폰의 예약 수 조회 (테스트용)
     */
    long countByCouponId(Long couponId);

    /**
     * 특정 쿠폰의 모든 예약 조회 (관리자용)
     */
    List<CouponReservation> findAllByCouponId(Long couponId);

    /**
     * 특정 상태의 예약 조회 (배치 복구용)
     * 예: RESERVED 상태로 오래 남아있는 것들 찾기
     */
    List<CouponReservation> findAllByCouponIdAndStatus(Long couponId, ReservationStatus status);
}
