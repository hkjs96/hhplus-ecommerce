package io.hhplus.ecommerce.application.dto.order;

import java.util.List;

public record OrderResponse(
    String orderId,
    List<OrderItemResponse> items,
    Long subtotalAmount,
    Long discountAmount,
    Long totalAmount,
    String status
) {
}
