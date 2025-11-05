package io.hhplus.ecommerce.application.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 충전 요청 DTO
 * - POST /users/{userId}/balance/charge
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChargeBalanceRequest {
    private Long amount;
}
