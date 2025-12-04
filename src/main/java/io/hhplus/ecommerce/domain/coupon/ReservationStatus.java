package io.hhplus.ecommerce.domain.coupon;

/**
 * 쿠폰 예약 상태
 *
 * - RESERVED: 선착순 자격 획득 (발급 대기 중)
 * - ISSUED: 쿠폰 발급 완료
 * - FAILED: 발급 실패 (재고 차감 실패 등)
 */
public enum ReservationStatus {
    /**
     * 선착순 자격 획득 완료
     * "100번째 안에 들었다" = 뒤집히지 않는 사실
     */
    RESERVED,

    /**
     * 쿠폰 발급 완료
     * UserCoupon 생성 및 Coupon.issuedQuantity 증가 완료
     */
    ISSUED,

    /**
     * 발급 실패
     * 재고 차감 또는 UserCoupon 생성 실패
     * Redis 순번은 원복됨
     */
    FAILED
}
