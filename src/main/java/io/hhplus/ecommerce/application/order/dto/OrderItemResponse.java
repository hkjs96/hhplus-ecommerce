package io.hhplus.ecommerce.application.order.dto;

import io.hhplus.ecommerce.domain.order.OrderItem;

public record OrderItemResponse(
    Long productId,
    String productName,
    Integer quantity,
    Long unitPrice,
    Long subtotal
) {
    public static OrderItemResponse of(Long productId, String productName, Integer quantity, Long unitPrice, Long subtotal) {
        return new OrderItemResponse(productId, productName, quantity, unitPrice, subtotal);
    }

    public static OrderItemResponse of(OrderItem item, String productName) {
        return new OrderItemResponse(
            item.getProductId(),
            productName,
            item.getQuantity(),
            item.getUnitPrice(),
            item.getSubtotal()
        );
    }
}
