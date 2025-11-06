package io.hhplus.ecommerce.application.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceResponse {

    private String userId;
    private Long balance;

    public static BalanceResponse of(String userId, Long balance) {
        return new BalanceResponse(userId, balance);
    }
}
