package io.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String userId;
    private List<OrderItemRequest> items;
    private String couponId;
}
