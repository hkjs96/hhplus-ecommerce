package io.hhplus.ecommerce.application.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ChargeBalanceResponse {
    private Long userId;
    private Long balance;
    private Long chargedAmount;
    private LocalDateTime chargedAt;

    public static ChargeBalanceResponse of(Long userId, Long balance, Long chargedAmount, LocalDateTime chargedAt) {
        return ChargeBalanceResponse.builder()
                .userId(userId)
                .balance(balance)
                .chargedAmount(chargedAmount)
                .chargedAt(chargedAt)
                .build();
    }
}
