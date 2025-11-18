package io.hhplus.ecommerce.application.order.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId
) {
}
