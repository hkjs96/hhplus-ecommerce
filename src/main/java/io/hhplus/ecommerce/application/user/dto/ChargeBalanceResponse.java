package io.hhplus.ecommerce.application.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 포인트 충전 응답 DTO
 * - 충전 후 잔액 정보 포함
 */
@Getter
@Builder
@AllArgsConstructor
public class ChargeBalanceResponse {
    private String userId;
    private Long balance;
    private Long chargedAmount;
    private LocalDateTime chargedAt;

    public static ChargeBalanceResponse of(String userId, Long balance, Long chargedAmount, LocalDateTime chargedAt) {
        return ChargeBalanceResponse.builder()
                .userId(userId)
                .balance(balance)
                .chargedAmount(chargedAmount)
                .chargedAt(chargedAt)
                .build();
    }
}
