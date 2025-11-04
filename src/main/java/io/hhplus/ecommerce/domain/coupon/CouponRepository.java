package io.hhplus.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface CouponRepository {

    /**
     * 쿠폰 ID로 조회
     */
    Optional<Coupon> findById(String id);

    /**
     * 모든 쿠폰 조회
     */
    List<Coupon> findAll();

    /**
     * 활성 쿠폰 조회 (현재 유효한 쿠폰)
     */
    List<Coupon> findActiveCoupons();

    /**
     * 쿠폰 저장 (생성 및 업데이트)
     */
    Coupon save(Coupon coupon);

    /**
     * 쿠폰 삭제
     */
    void deleteById(String id);

    /**
     * 쿠폰 존재 여부 확인
     */
    boolean existsById(String id);
}
