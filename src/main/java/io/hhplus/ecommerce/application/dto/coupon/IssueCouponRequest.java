package io.hhplus.ecommerce.application.dto.coupon;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponRequest {
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;
}
