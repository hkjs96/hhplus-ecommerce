package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_coupon", columnNames = {"user_id", "coupon_id"})
    },
    indexes = {
        @Index(name = "idx_user_status", columnList = "user_id, status"),
        @Index(name = "idx_coupon_id", columnList = "coupon_id")
    }
)
@Getter
@NoArgsConstructor
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;  // FK to users

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;  // FK to coupons

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static UserCoupon create(Long userId, Long couponId, LocalDateTime expiresAt) {
        validateUserId(userId);
        validateCouponId(couponId);
        validateExpiresAt(expiresAt);

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.userId = userId;
        userCoupon.couponId = couponId;
        userCoupon.status = CouponStatus.AVAILABLE;
        userCoupon.issuedAt = LocalDateTime.now();
        userCoupon.usedAt = null;  // 사용 시 설정
        userCoupon.expiresAt = expiresAt;

        return userCoupon;
    }

    @PrePersist
    protected void onCreate() {
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
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

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }

    private static void validateCouponId(Long couponId) {
        if (couponId == null) {
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
