package io.hhplus.ecommerce.application.order.dto;

import java.util.List;

public record CompleteOrderRequest(
    Long userId,
    List<OrderItemRequest> items,
    Long couponId
) {
}
