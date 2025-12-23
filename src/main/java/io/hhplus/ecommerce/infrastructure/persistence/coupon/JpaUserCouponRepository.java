package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponProjection;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // ============================================================
    // Performance Optimization: Native Query for User Coupons
    // ============================================================

    /**
     * 사용자 쿠폰 조회 (사용자 쿠폰 + 쿠폰 정보 포함)
     *
     * <p>최적화 전략:
     * <ul>
     *   <li>N+1 문제 해결: 단일 JOIN 쿼리로 모든 데이터 조회</li>
     *   <li>인덱스 사용: idx_user_coupons_user_status, idx_user_coupons_coupon_id</li>
     *   <li>예상 성능: 500ms → 50ms (90% 개선)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 (null 가능)
     * @return 사용자 쿠폰 + 쿠폰 정보 목록
     */
    @Query(value = """
        SELECT
            uc.id AS userCouponId,
            uc.user_id AS userId,
            uc.coupon_id AS couponId,
            uc.status AS status,
            uc.issued_at AS issuedAt,
            uc.used_at AS usedAt,
            c.name AS couponName,
            c.discount_rate AS discountRate,
            c.expires_at AS expiresAt
        FROM user_coupons uc
        JOIN coupons c ON uc.coupon_id = c.id
        WHERE uc.user_id = :userId
          AND (:status IS NULL OR uc.status = :status)
        ORDER BY uc.issued_at DESC
        """, nativeQuery = true)
    List<UserCouponProjection> findUserCouponsWithDetails(
        @Param("userId") Long userId,
        @Param("status") String status
    );
}
