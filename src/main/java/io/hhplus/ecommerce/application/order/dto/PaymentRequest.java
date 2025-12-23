package io.hhplus.ecommerce.application.order.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId,

    /**
     * 멱등성 키 (중복 결제 방지)
     * - 클라이언트가 UUID 등으로 생성하여 제공
     * - 동일한 키로 재요청 시 기존 결과 반환
     * - PROCESSING 상태인 경우 409 Conflict
     */
    @NotNull(message = "멱등성 키는 필수입니다")
    String idempotencyKey
) {
    /**
     * PaymentRequest 생성 (Phase 3용)
     */
    public static PaymentRequest of(Long userId, String idempotencyKey) {
        return new PaymentRequest(userId, idempotencyKey);
    }
}
