package io.hhplus.ecommerce.application.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponRequest {
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
}
