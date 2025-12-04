package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.CouponReservation;
import io.hhplus.ecommerce.domain.coupon.CouponReservationRepository;
import io.hhplus.ecommerce.domain.coupon.ReservationStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 예약 JPA Repository 구현체
 */
@Repository
@Primary
public interface JpaCouponReservationRepository extends JpaRepository<CouponReservation, Long>, CouponReservationRepository {

    @Override
    CouponReservation save(CouponReservation reservation);

    @Override
    Optional<CouponReservation> findById(Long id);

    @Override
    Optional<CouponReservation> findByUserIdAndCouponId(Long userId, Long couponId);

    @Override
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @Override
    long countByCouponId(Long couponId);

    @Override
    List<CouponReservation> findAllByCouponId(Long couponId);

    @Override
    List<CouponReservation> findAllByCouponIdAndStatus(Long couponId, ReservationStatus status);
}
