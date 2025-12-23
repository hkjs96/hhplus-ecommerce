package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 예약 엔티티
 *
 * 선착순 자격 획득을 기록하는 테이블 (뒤집히지 않는 사실)
 * - UserCoupon: 실제 쿠폰 발급 완료 기록 (AVAILABLE → USED → EXPIRED)
 * - CouponReservation: 선착순 자격 획득 기록 (RESERVED → ISSUED)
 *
 * 분리 이유:
 * - "100번째 안에 들었다" (예약) vs "쿠폰이 내 계정에 있다" (발급)
 * - 선착순 판정 (Redis INCR + DB 예약) 과 쿠폰 발급 (재고 차감 + UserCoupon) 분리
 */
@Entity
@Table(
    name = "coupon_reservations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_coupon_reservation",
            columnNames = {"user_id", "coupon_id"}
        )
    },
    indexes = {
        @Index(name = "idx_coupon_status", columnList = "coupon_id, status"),
        @Index(name = "idx_coupon_sequence", columnList = "coupon_id, sequence_number"),
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    /**
     * Redis INCR 결과 (선착순 순번)
     * 예: 1, 2, 3, ..., 100
     */
    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "reserved_at", nullable = false, updatable = false)
    private LocalDateTime reservedAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * 선착순 예약 생성
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param sequenceNumber Redis INCR 결과 (순번)
     * @return 예약 엔티티
     */
    public static CouponReservation create(Long userId, Long couponId, Long sequenceNumber) {
        validateUserId(userId);
        validateCouponId(couponId);
        validateSequenceNumber(sequenceNumber);

        CouponReservation reservation = new CouponReservation();
        reservation.userId = userId;
        reservation.couponId = couponId;
        reservation.sequenceNumber = sequenceNumber;
        reservation.status = ReservationStatus.RESERVED;
        reservation.reservedAt = LocalDateTime.now();
        reservation.issuedAt = null;
        reservation.failedAt = null;
        reservation.failureReason = null;

        return reservation;
    }

    @PrePersist
    protected void onCreate() {
        if (this.reservedAt == null) {
            this.reservedAt = LocalDateTime.now();
        }
    }

    /**
     * 발급 완료 처리
     * RESERVED → ISSUED
     */
    public void markAsIssued() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                String.format("예약 상태가 RESERVED가 아닙니다. 현재 상태: %s", this.status)
            );
        }

        this.status = ReservationStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 발급 실패 처리
     * RESERVED → FAILED
     *
     * @param failureReason 실패 사유
     */
    public void markAsFailed(String failureReason) {
        if (this.status != ReservationStatus.RESERVED) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                String.format("예약 상태가 RESERVED가 아닙니다. 현재 상태: %s", this.status)
            );
        }

        this.status = ReservationStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.failureReason = failureReason;
    }

    public boolean isReserved() {
        return this.status == ReservationStatus.RESERVED;
    }

    public boolean isIssued() {
        return this.status == ReservationStatus.ISSUED;
    }

    public boolean isFailed() {
        return this.status == ReservationStatus.FAILED;
    }

    // ====================================
    // Validation Methods
    // ====================================

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

    private static void validateSequenceNumber(Long sequenceNumber) {
        if (sequenceNumber == null || sequenceNumber <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "순번은 1 이상이어야 합니다"
            );
        }
    }
}
