package io.hhplus.ecommerce.application.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {
    @NotBlank(message = "장바구니 항목 ID는 필수입니다")
    private String cartItemId;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer quantity;
}
