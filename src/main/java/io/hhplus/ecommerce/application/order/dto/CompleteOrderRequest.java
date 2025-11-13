package io.hhplus.ecommerce.application.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CompleteOrderRequest {
    private Long userId;
    private List<OrderItemRequest> items;
    private Long couponId;
}
