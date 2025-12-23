package io.hhplus.ecommerce.application.cart.dto;

import java.util.List;

public record CartResponse(
    Long userId,
    List<CartItemResponse> items,
    Long totalAmount
) {
    public static CartResponse of(Long userId, List<CartItemResponse> items) {
        Long totalAmount = items.stream()
            .mapToLong(CartItemResponse::subtotal)
            .sum();

        return new CartResponse(userId, items, totalAmount);
    }
}
