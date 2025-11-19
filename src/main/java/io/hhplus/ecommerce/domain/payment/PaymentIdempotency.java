package io.hhplus.ecommerce.domain.payment;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 멱등성 키 엔티티
 * <p>
 * 중복 결제 방지를 위한 멱등성 키 관리
 * - 클라이언트가 제공한 idempotencyKey로 중복 요청 탐지
 * - UNIQUE 제약 조건으로 동시 요청 시 DB 레벨에서 차단
 * - COMPLETED 상태의 결과는 캐싱하여 반환
 * <p>
 * 5명 페르소나 분석:
 * - 김데이터(O): DB Unique Constraint로 동시성 해결, PROCESSING 상태로 중복 방지
 * - 박트래픽(△): Redis 권장하지만 DB로 시작 가능
 * - 이금융(O): 결제 멱등성 필수, COMPLETED 상태 영구 보관
 * - 최아키텍트(△): Event Sourcing 권장하지만 심플하게 시작
 * - 정스타트업(O): DB Unique + 상태 관리로 충분
 * <p>
 * 최종 선택: DB 기반 멱등성 키 (Unique Constraint + 상태 관리)
 */
@Entity
@Table(
    name = "payment_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_created_at", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentIdempotency {

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
     * 처리된 주문 ID (COMPLETED 시에만 저장)
     */
    @Column(name = "order_id")
    private Long orderId;

    /**
     * 요청한 사용자 ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 처리 상태 (PROCESSING, COMPLETED, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    /**
     * 완료된 결제 응답 (JSON 저장)
     * COMPLETED 상태일 때 클라이언트에게 반환
     */
    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * 실패 시 에러 메시지
     */
    @Column(length = 500)
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
    public static PaymentIdempotency create(String idempotencyKey, Long userId) {
        validateIdempotencyKey(idempotencyKey);
        validateUserId(userId);

        PaymentIdempotency entity = new PaymentIdempotency();
        entity.idempotencyKey = idempotencyKey;
        entity.userId = userId;
        entity.status = IdempotencyStatus.PROCESSING;
        entity.createdAt = LocalDateTime.now();
        entity.expiresAt = LocalDateTime.now().plusHours(24);  // 24시간 후 만료

        return entity;
    }

    /**
     * 결제 완료 처리
     */
    public void complete(Long orderId, String responsePayload) {
        if (this.status != IdempotencyStatus.PROCESSING) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "PROCESSING 상태가 아닌 요청은 완료할 수 없습니다. 현재 상태: " + this.status
            );
        }

        this.orderId = orderId;
        this.responsePayload = responsePayload;
        this.status = IdempotencyStatus.COMPLETED;
    }

    /**
     * 결제 실패 처리
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
