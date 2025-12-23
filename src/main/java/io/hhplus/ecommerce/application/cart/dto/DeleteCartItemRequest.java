package io.hhplus.ecommerce.application.cart.dto;

import jakarta.validation.constraints.NotNull;

public record DeleteCartItemRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId,

    @NotNull(message = "상품 ID는 필수입니다")
    Long productId
) {
}
