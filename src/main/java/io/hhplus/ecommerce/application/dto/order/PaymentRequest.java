package io.hhplus.ecommerce.application.dto.order;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(
    @NotBlank(message = "사용자 ID는 필수입니다")
    String userId
) {
}
