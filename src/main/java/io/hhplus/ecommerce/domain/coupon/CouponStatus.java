package io.hhplus.ecommerce.domain.coupon;

/**
 * 쿠폰 상태
 */
public enum CouponStatus {
    /**
     * 사용 가능
     */
    AVAILABLE,

    /**
     * 사용됨
     */
    USED,

    /**
     * 만료됨
     */
    EXPIRED
}
