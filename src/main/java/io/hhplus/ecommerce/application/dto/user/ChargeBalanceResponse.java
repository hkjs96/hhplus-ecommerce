package io.hhplus.ecommerce.application.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChargeBalanceResponse {
    private String userId;
    private Integer chargedAmount;
    private Integer currentBalance;
    private LocalDateTime chargedAt;
}
