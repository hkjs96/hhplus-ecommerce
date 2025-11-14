package io.hhplus.ecommerce.application.dto.user;

import java.time.LocalDateTime;

public record ChargeBalanceResponse(
    String userId,
    Integer chargedAmount,
    Integer currentBalance,
    LocalDateTime chargedAt
) {
}
