package io.hhplus.ecommerce.application.dto.order;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    private String couponId; // Optional
}
