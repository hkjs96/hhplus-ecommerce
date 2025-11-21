package io.hhplus.ecommerce.application.cart.dto;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.product.Product;

public record CartItemResponse(
    Long productId,
    String name,
    Long unitPrice,
    Integer quantity,
    Long subtotal,
    Boolean stockAvailable
) {
    public static CartItemResponse of(CartItem cartItem, Product product) {
        Long subtotal = product.getPrice() * cartItem.getQuantity();
        Boolean stockAvailable = product.getStock() >= cartItem.getQuantity();

        return new CartItemResponse(
            product.getId(),
            product.getName(),
            product.getPrice(),
            cartItem.getQuantity(),
            subtotal,
            stockAvailable
        );
    }

    public static CartItemResponse forUpdate(Long productId, Integer quantity, Long subtotal) {
        return new CartItemResponse(
            productId,
            null,
            null,
            quantity,
            subtotal,
            null
        );
    }
}
