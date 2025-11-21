package io.hhplus.ecommerce.application.user.dto;

public record BalanceResponse(
    Long userId,
    Long balance
) {
    public static BalanceResponse of(Long userId, Long balance) {
        return new BalanceResponse(userId, balance);
    }
}
