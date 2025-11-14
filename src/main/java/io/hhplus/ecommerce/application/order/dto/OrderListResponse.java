package io.hhplus.ecommerce.application.order.dto;

import java.util.List;

public record OrderListResponse(
    List<CreateOrderResponse> orders,
    Integer totalCount
) {
    public static OrderListResponse of(List<CreateOrderResponse> orders) {
        return new OrderListResponse(orders, orders.size());
    }
}
