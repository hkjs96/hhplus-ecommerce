package io.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderItemResponse {
    private String productId;
    private String name;
    private Integer quantity;
    private Long unitPrice;
    private Long subtotal;

    public static OrderItemResponse of(String productId, String productName, Integer quantity, Long unitPrice, Long subtotal) {
        return new OrderItemResponse(productId, productName, quantity, unitPrice, subtotal);
    }
}
