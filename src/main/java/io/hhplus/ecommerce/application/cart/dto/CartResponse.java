package io.hhplus.ecommerce.application.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CartResponse {
    private Long userId;
    private List<CartItemResponse> items;
    private Long totalAmount;

    public static CartResponse of(Long userId, List<CartItemResponse> items) {
        Long totalAmount = items.stream()
            .mapToLong(CartItemResponse::getSubtotal)
            .sum();

        return new CartResponse(userId, items, totalAmount);
    }
}
