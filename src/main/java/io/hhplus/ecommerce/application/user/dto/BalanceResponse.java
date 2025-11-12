package io.hhplus.ecommerce.application.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceResponse {

    private Long userId;
    private Long balance;

    public static BalanceResponse of(Long userId, Long balance) {
        return new BalanceResponse(userId, balance);
    }
}
