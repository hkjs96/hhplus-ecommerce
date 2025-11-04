package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 엔티티 (Rich Domain Model)
 * Week 3: Pure Java Entity (JPA 어노테이션 없음)
 *
 * 비즈니스 규칙:
 * - 한 사용자는 같은 쿠폰을 1번만 발급받을 수 있음
 * - 사용 후 status를 USED로 변경
 * - 만료일이 지나면 status를 EXPIRED로 변경
 */
@Getter
@AllArgsConstructor
public class UserCoupon {

    private String id;
    private String userId;
    private String couponId;
    private CouponStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;

    /**
     * 사용자 쿠폰 생성 (Factory Method)
     */
    public static UserCoupon create(String id, String userId, String couponId, LocalDateTime expiresAt) {
        validateUserId(userId);
        validateCouponId(couponId);
        validateExpiresAt(expiresAt);

        LocalDateTime now = LocalDateTime.now();

        return new UserCoupon(
            id,
            userId,
            couponId,
            CouponStatus.AVAILABLE,
            now,
            null,  // usedAt은 사용 시 설정
            expiresAt
        );
    }

    /**
     * 쿠폰 사용 (비즈니스 로직)
     *
     * @throws BusinessException 쿠폰이 이미 사용되었거나 만료된 경우
     */
    public void use() {
        validateAvailableForUse();

        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 만료 처리
     */
    public void expire() {
        if (this.status == CouponStatus.AVAILABLE) {
            this.status = CouponStatus.EXPIRED;
        }
    }

    /**
     * 쿠폰 사용 가능 여부 확인
     */
    public boolean isAvailable() {
        return this.status == CouponStatus.AVAILABLE && !isExpired();
    }

    /**
     * 쿠폰 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * 쿠폰 사용됨 여부 확인
     */
    public boolean isUsed() {
        return this.status == CouponStatus.USED;
    }

    // ====================================
    // Validation Methods
    // ====================================

    private void validateAvailableForUse() {
        if (this.status != CouponStatus.AVAILABLE) {
            throw new BusinessException(
                ErrorCode.INVALID_COUPON,
                String.format("쿠폰을 사용할 수 없습니다. 현재 상태: %s", this.status)
            );
        }

        if (isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_COUPON);
        }
    }

    private static void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }

    private static void validateCouponId(String couponId) {
        if (couponId == null || couponId.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 ID는 필수입니다"
            );
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 만료일은 필수입니다"
            );
        }
    }
}
