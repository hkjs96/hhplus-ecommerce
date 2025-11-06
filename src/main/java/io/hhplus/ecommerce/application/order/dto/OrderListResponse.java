package io.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OrderListResponse {
    private List<CreateOrderResponse> orders;
    private Integer totalCount;

    public static OrderListResponse of(List<CreateOrderResponse> orders) {
        return new OrderListResponse(orders, orders.size());
    }
}
