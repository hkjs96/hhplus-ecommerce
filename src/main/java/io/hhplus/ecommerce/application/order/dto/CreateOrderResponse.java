package io.hhplus.ecommerce.application.order.dto;

import io.hhplus.ecommerce.domain.order.Order;

import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderResponse(
    Long orderId,
    Long userId,
    String orderNumber,
    List<OrderItemResponse> items,
    Long subtotalAmount,
    Long discountAmount,
    Long totalAmount,
    String status,
    LocalDateTime createdAt
) {
    public static CreateOrderResponse of(Order order, List<OrderItemResponse> items) {
        return new CreateOrderResponse(
                order.getId(),
                order.getUserId(),
                order.getOrderNumber(),
                items,
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
