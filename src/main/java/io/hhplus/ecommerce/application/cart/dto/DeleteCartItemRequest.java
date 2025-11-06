package io.hhplus.ecommerce.application.cart.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCartItemRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotBlank(message = "상품 ID는 필수입니다")
    private String productId;
}
