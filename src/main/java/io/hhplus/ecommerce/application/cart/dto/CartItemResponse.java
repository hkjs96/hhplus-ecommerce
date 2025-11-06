package io.hhplus.ecommerce.application.cart.dto;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.product.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CartItemResponse {
    private String productId;
    private String name;
    private Long unitPrice;
    private Integer quantity;
    private Long subtotal;
    private Boolean stockAvailable;

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

    public static CartItemResponse forUpdate(String productId, Integer quantity, Long subtotal) {
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
