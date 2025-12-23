package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.domain.payment.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 주문 생성 멱등성 보장을 위한 Entity
 * <p>
 * 목적:
 * - 중복 주문 방지 (사용자가 주문 버튼 두 번 클릭)
 * - 네트워크 타임아웃 후 재시도 안전성
 * - 재고 이중 차감 방지
 * - 응답 캐싱 (동일 요청의 빠른 응답)
 * <p>
 * 설계:
 * - Unique Constraint: idempotency_key (DB 레벨 중복 방지)
 * - 상태 관리: PROCESSING → COMPLETED / FAILED
 * - 24시간 TTL (expires_at)
 * - 응답 캐싱 (response_payload)
 * <p>
 * 사용 예시:
 * <pre>
 * // 첫 번째 주문 요청
 * idempotencyKey: "xyz-789"
 * → DB 저장: PROCESSING → 주문 생성 → COMPLETED
 *
 * // 두 번째 주문 요청 (같은 키, 네트워크 타임아웃 후 재시도)
 * idempotencyKey: "xyz-789"
 * → DB 조회: COMPLETED → 캐시된 응답 반환 (주문 안 만듦!)
 * </pre>
 */
@Entity
@Table(
    name = "order_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_order_idempotency_key",
            columnNames = "idempotency_key"
        )
    },
    indexes = {
        @Index(name = "idx_order_user_id", columnList = "user_id"),
        @Index(name = "idx_order_expires_at", columnList = "expires_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 멱등성 키 (UUID)
     * - 클라이언트에서 생성
     * - 동일 요청 식별자
     * - Unique Constraint 적용
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /**
     * 사용자 ID
     * - 누가 주문했는지 추적
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 총 금액
     * - 추적 및 로깅용
     */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    /**
     * 생성된 주문 ID
     * - 응답에 포함될 주문 ID
     * - COMPLETED 상태일 때만 저장
     */
    @Column(name = "created_order_id")
    private Long createdOrderId;

    /**
     * 처리 상태
     * - PROCESSING: 처리 중
     * - COMPLETED: 완료
     * - FAILED: 실패
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    /**
     * 응답 캐싱 (JSON)
     * - COMPLETED 상태일 때만 저장
     * - 동일 요청 시 즉시 반환
     */
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * 에러 메시지
     * - FAILED 상태일 때만 저장
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 만료 시간
     * - 생성 후 24시간
     * - Batch Job으로 주기적으로 삭제
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 생성 시간
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 시간
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Factory Method =====

    /**
     * 새로운 멱등성 키 생성 (PROCESSING 상태)
     */
    public static OrderIdempotency create(String idempotencyKey, Long userId, Long totalAmount) {
        OrderIdempotency entity = new OrderIdempotency();
        entity.idempotencyKey = idempotencyKey;
        entity.userId = userId;
        entity.totalAmount = totalAmount;
        entity.status = IdempotencyStatus.PROCESSING;
        entity.expiresAt = LocalDateTime.now().plusDays(1);  // 24시간 후 만료
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    // ===== Business Logic =====

    /**
     * 완료 처리 (응답 캐싱)
     */
    public void complete(Long createdOrderId, String responsePayload) {
        this.createdOrderId = createdOrderId;
        this.responsePayload = responsePayload;
        this.status = IdempotencyStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 실패 처리
     */
    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = IdempotencyStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 완료 상태 확인
     */
    public boolean isCompleted() {
        return this.status == IdempotencyStatus.COMPLETED;
    }

    /**
     * 처리 중 상태 확인
     */
    public boolean isProcessing() {
        return this.status == IdempotencyStatus.PROCESSING;
    }

    /**
     * 만료 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
