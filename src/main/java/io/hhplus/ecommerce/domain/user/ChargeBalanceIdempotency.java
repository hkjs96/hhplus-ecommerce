package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.payment.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 잔액 충전 멱등성 키 엔티티
 * <p>
 * 중복 충전 방지를 위한 멱등성 키 관리
 * - 클라이언트가 제공한 idempotencyKey로 중복 요청 탐지
 * - UNIQUE 제약 조건으로 동시 요청 시 DB 레벨에서 차단
 * - COMPLETED 상태의 결과는 캐싱하여 반환
 * <p>
 * 시나리오:
 * 1. 버튼 두 번 클릭 → 같은 idempotencyKey → 중복 방지
 * 2. 네트워크 타임아웃 후 재시도 → 같은 key → 캐시된 응답 반환
 * 3. 정상 충전 → PROCESSING → COMPLETED → 응답 캐싱
 */
@Entity
@Table(
    name = "charge_balance_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_charge_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_charge_user_id", columnList = "user_id"),
        @Index(name = "idx_charge_created_at", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargeBalanceIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 클라이언트 제공 멱등성 키 (UUID 권장)
     * UNIQUE 제약 조건으로 중복 요청 차단
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /**
     * 요청한 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 충전 금액
     */
    @Column(nullable = false)
    private Long amount;

    /**
     * 처리 상태 (PROCESSING, COMPLETED, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    /**
     * 완료된 충전 응답 (JSON 저장)
     * COMPLETED 상태일 때 클라이언트에게 반환
     */
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * 실패 시 에러 메시지
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 만료 시간 (기본 24시간)
     * 만료된 키는 재사용 가능
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 멱등성 키 생성 (PROCESSING 상태로 시작)
     */
    public static ChargeBalanceIdempotency create(String idempotencyKey, User user, Long amount) {
        validateIdempotencyKey(idempotencyKey);
        validateUserId(user.getId());
        validateAmount(amount);

        ChargeBalanceIdempotency entity = new ChargeBalanceIdempotency();
        entity.idempotencyKey = idempotencyKey;
        entity.userId = user.getId();
        entity.amount = amount;
        entity.status = IdempotencyStatus.PROCESSING;
        entity.createdAt = LocalDateTime.now();
        entity.expiresAt = LocalDateTime.now().plusHours(24);  // 24시간 후 만료

        return entity;
    }

    /**
     * 충전 완료 처리
     */
    public void complete(String responsePayload) {
        if (this.status != IdempotencyStatus.PROCESSING) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "PROCESSING 상태가 아닌 요청은 완료할 수 없습니다. 현재 상태: " + this.status
            );
        }

        this.responsePayload = responsePayload;
        this.status = IdempotencyStatus.COMPLETED;
    }

    /**
     * 충전 실패 처리
     */
    public void fail(String errorMessage) {
        if (this.status != IdempotencyStatus.PROCESSING) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "PROCESSING 상태가 아닌 요청은 실패 처리할 수 없습니다. 현재 상태: " + this.status
            );
        }

        this.errorMessage = errorMessage;
        this.status = IdempotencyStatus.FAILED;
    }

    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 처리 중인지 확인
     */
    public boolean isProcessing() {
        return this.status == IdempotencyStatus.PROCESSING && !isExpired();
    }

    /**
     * 완료되었는지 확인
     */
    public boolean isCompleted() {
        return this.status == IdempotencyStatus.COMPLETED;
    }

    /**
     * 실패했는지 확인
     */
    public boolean isFailed() {
        return this.status == IdempotencyStatus.FAILED;
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "멱등성 키는 필수입니다"
            );
        }
        if (idempotencyKey.length() > 100) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "멱등성 키는 100자를 초과할 수 없습니다"
            );
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }

    private static void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "충전 금액은 0보다 커야 합니다"
            );
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.expiresAt == null) {
            this.expiresAt = LocalDateTime.now().plusHours(24);
        }
    }
}
