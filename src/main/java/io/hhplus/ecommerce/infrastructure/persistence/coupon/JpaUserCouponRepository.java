package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
public interface JpaUserCouponRepository extends JpaRepository<UserCoupon, Long>, UserCouponRepository {

    @Override
    UserCoupon save(UserCoupon userCoupon);

    @Override
    List<UserCoupon> findByUserId(Long userId);

    @Override
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
