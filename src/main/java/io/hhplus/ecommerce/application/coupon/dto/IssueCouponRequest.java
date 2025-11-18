package io.hhplus.ecommerce.application.coupon.dto;

import jakarta.validation.constraints.NotNull;

public record IssueCouponRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId
) {
}
