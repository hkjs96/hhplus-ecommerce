package io.hhplus.ecommerce.domain.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 실패한 이벤트 저장 엔티티 (Outbox Pattern)
 *
 * 목적:
 * - 이벤트 처리 실패 시 재시도를 위해 DB에 저장
 * - 재시도 횟수 및 실패 사유 기록
 * - 스케줄러가 주기적으로 재처리
 *
 * 상태:
 * - PENDING: 재시도 대기
 * - RETRYING: 재시도 중
 * - SUCCESS: 재시도 성공
 * - FAILED: 최종 실패 (DLQ)
 */
@Entity
@Table(name = "failed_events", indexes = {
    @Index(name = "idx_failed_events_status", columnList = "status"),
    @Index(name = "idx_failed_events_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이벤트 타입 (예: "PaymentCompleted")
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * 이벤트 고유 ID (예: "order-123")
     */
    @Column(nullable = false, length = 255)
    private String eventId;

    /**
     * 이벤트 페이로드 (JSON 형식)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * 실패 사유
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    private int retryCount = 0;

    /**
     * 최대 재시도 횟수
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailedEventStatus status = FailedEventStatus.PENDING;

    /**
     * 생성 일시
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 다음 재시도 예정 시각
     */
    private LocalDateTime nextRetryAt;

    // ===== 생성 메서드 =====

    public static FailedEvent create(String eventType, String eventId, String payload, String errorMessage) {
        FailedEvent event = new FailedEvent();
        event.eventType = eventType;
        event.eventId = eventId;
        event.payload = payload;
        event.errorMessage = errorMessage;
        event.status = FailedEventStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();
        event.nextRetryAt = LocalDateTime.now().plusMinutes(1);  // 1분 후 재시도

        return event;
    }

    // ===== 비즈니스 로직 =====

    /**
     * 재시도 시작
     */
    public void startRetry() {
        if (this.status != FailedEventStatus.PENDING) {
            throw new IllegalStateException("재시도 가능한 상태가 아닙니다: " + this.status);
        }

        this.status = FailedEventStatus.RETRYING;
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재시도 성공
     */
    public void markSuccess() {
        this.status = FailedEventStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    /**
     * 재시도 실패 (다시 PENDING으로)
     */
    public void markRetryFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();

        if (this.retryCount >= MAX_RETRY_COUNT) {
            // 최대 재시도 횟수 초과 → DLQ
            this.status = FailedEventStatus.FAILED;
            this.nextRetryAt = null;
        } else {
            // 재시도 대기
            this.status = FailedEventStatus.PENDING;
            // Exponential Backoff: 1분, 2분, 4분
            long delayMinutes = (long) Math.pow(2, this.retryCount - 1);
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    /**
     * 재시도 가능 여부
     */
    public boolean canRetry() {
        return this.status == FailedEventStatus.PENDING
            && this.retryCount < MAX_RETRY_COUNT
            && this.nextRetryAt != null
            && LocalDateTime.now().isAfter(this.nextRetryAt);
    }

    // ===== Enum =====

    public enum FailedEventStatus {
        PENDING,   // 재시도 대기
        RETRYING,  // 재시도 중
        SUCCESS,   // 성공
        FAILED     // 최종 실패 (DLQ)
    }
}
