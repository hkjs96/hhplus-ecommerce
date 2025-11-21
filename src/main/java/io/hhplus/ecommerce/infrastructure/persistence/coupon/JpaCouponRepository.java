package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
public interface JpaCouponRepository extends JpaRepository<Coupon, Long>, CouponRepository {

    // Explicitly declare methods to resolve ambiguity with CouponRepository
    @Override
    Optional<Coupon> findById(Long id);

    @Override
    Coupon save(Coupon coupon);

    @Override
    Optional<Coupon> findByCouponCode(String couponCode);

    /**
     * Pessimistic Write Lock (SELECT FOR UPDATE)
     * - 쿠폰 발급 시 사용
     * - 선착순 이벤트에서 정확한 수량 제어
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);
}
