package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
