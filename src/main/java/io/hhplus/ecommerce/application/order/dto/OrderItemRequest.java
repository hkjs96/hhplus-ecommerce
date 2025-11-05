package io.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
    private String productId;
    private Integer quantity;
}
