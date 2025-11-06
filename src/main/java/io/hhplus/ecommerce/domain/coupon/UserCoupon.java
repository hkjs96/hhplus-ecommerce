package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

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

    public void use() {
        validateAvailableForUse();

        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void expire() {
        if (this.status == CouponStatus.AVAILABLE) {
            this.status = CouponStatus.EXPIRED;
        }
    }

    public boolean isAvailable() {
        return this.status == CouponStatus.AVAILABLE && !isExpired();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

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
