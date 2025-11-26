package io.hhplus.ecommerce.application.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 잔액 충전 요청 DTO
 * <p>
 * 멱등성 보장을 위해 idempotencyKey 필수
 * - 클라이언트는 UUID 등으로 고유 키 생성
 * - 동일 키로 재시도 시 중복 충전 방지
 */
public record ChargeBalanceRequest(
    @NotNull(message = "충전 금액은 필수입니다")
    @Min(value = 1, message = "충전 금액은 1원 이상이어야 합니다")
    Long amount,

    @NotBlank(message = "멱등성 키는 필수입니다")
    String idempotencyKey
) {
}
