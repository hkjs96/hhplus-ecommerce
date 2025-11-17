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
    name = "coupons",
    indexes = {
        @Index(name = "idx_coupon_code", columnList = "coupon_code"),
        @Index(name = "idx_date_range", columnList = "start_date, end_date")
    }
)
@Getter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_code", unique = true, length = 20, nullable = false)
    private String couponCode;  // Business ID (외부 노출용, e.g., "COUPON-2025-001")

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "discount_rate", nullable = false)
    private Integer discountRate;      // 할인율 (%)

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;     // 총 수량

    @Column(name = "issued_quantity", nullable = false)
    private Integer issuedQuantity;    // 발급된 수량 (JPA @Version으로 동시성 제어)

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Version
    private Long version;  // Optimistic Lock for concurrent coupon issuance

    public static Coupon create(String couponCode, String name, Integer discountRate, Integer totalQuantity, LocalDateTime startDate, LocalDateTime endDate) {
        validateCouponCode(couponCode);
        validateDiscountRate(discountRate);
        validateTotalQuantity(totalQuantity);
        validateDateRange(startDate, endDate);

        Coupon coupon = new Coupon();
        coupon.couponCode = couponCode;
        coupon.name = name;
        coupon.discountRate = discountRate;
        coupon.totalQuantity = totalQuantity;
        coupon.issuedQuantity = 0;
        coupon.startDate = startDate;
        coupon.endDate = endDate;

        return coupon;
    }

    public void issue() {
        if (issuedQuantity >= totalQuantity) {
            throw new BusinessException(
                ErrorCode.COUPON_SOLD_OUT,
                "쿠폰이 모두 소진되었습니다. couponId: " + this.id
            );
        }
        this.issuedQuantity++;
    }

    public boolean tryIssue() {
        if (issuedQuantity >= totalQuantity) {
            return false;
        }
        this.issuedQuantity++;
        return true;
    }

    public boolean isValid(LocalDateTime now) {
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(endDate);
    }

    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity;
    }

    public LocalDateTime getExpiresAt() {
        return this.endDate;
    }

    public void validateIssuable() {
        if (isExpired(LocalDateTime.now())) {
            throw new BusinessException(
                ErrorCode.EXPIRED_COUPON,
                "만료된 쿠폰입니다. couponId: " + this.id
            );
        }
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateCouponCode(String couponCode) {
        if (couponCode == null || couponCode.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 코드는 필수입니다"
            );
        }
    }

    private static void validateDiscountRate(Integer discountRate) {
        if (discountRate == null || discountRate < 0 || discountRate > 100) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인율은 0~100 사이여야 합니다"
            );
        }
    }

    private static void validateTotalQuantity(Integer totalQuantity) {
        if (totalQuantity == null || totalQuantity <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 수량은 1 이상이어야 합니다"
            );
        }
    }

    private static void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 유효기간은 필수입니다"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "시작일은 종료일보다 이전이어야 합니다"
            );
        }
    }
}
