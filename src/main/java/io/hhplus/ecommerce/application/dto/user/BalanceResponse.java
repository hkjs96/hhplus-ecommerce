package io.hhplus.ecommerce.application.dto.user;

public record BalanceResponse(
    String userId,
    Integer balance
) {
}
