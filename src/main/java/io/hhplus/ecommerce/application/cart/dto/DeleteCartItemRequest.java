package io.hhplus.ecommerce.application.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCartItemRequest {
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "상품 ID는 필수입니다")
    private Long productId;
}
