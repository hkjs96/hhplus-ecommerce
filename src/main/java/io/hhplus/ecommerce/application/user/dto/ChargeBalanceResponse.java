package io.hhplus.ecommerce.application.user.dto;

import java.time.LocalDateTime;

public record ChargeBalanceResponse(
    Long userId,
    Long balance,
    Long chargedAmount,
    LocalDateTime chargedAt
) {
    public static ChargeBalanceResponse of(Long userId, Long balance, Long chargedAmount, LocalDateTime chargedAt) {
        return new ChargeBalanceResponse(userId, balance, chargedAmount, chargedAt);
    }
}
