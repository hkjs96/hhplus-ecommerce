package io.hhplus.ecommerce.application.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CartItemResponse {
    private String cartItemId;
    private String productId;
    private String name;
    private Long unitPrice;
    private Integer quantity;
    private Long subtotal;
    private Boolean stockAvailable;
    private Integer currentStock;
}
