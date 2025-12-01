package io.hhplus.ecommerce.application.cart.dto;

import java.util.ArrayList;
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

        // ArrayList로 고정해 직렬화 시 타입 정보가 불필요하도록 한다.
        return new CartResponse(userId, new ArrayList<>(items), totalAmount);
    }
}
