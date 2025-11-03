package io.hhplus.ecommerce.application.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BalanceResponse {
    private String userId;
    private Integer balance;
}
