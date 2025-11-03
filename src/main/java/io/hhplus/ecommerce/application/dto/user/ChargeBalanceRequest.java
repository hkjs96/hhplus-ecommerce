package io.hhplus.ecommerce.application.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChargeBalanceRequest {
    @NotNull(message = "충전 금액은 필수입니다")
    @Min(value = 1000, message = "최소 충전 금액은 1000원입니다")
    private Integer amount;
}
