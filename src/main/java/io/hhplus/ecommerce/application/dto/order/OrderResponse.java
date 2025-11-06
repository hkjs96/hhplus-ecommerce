package io.hhplus.ecommerce.application.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private List<OrderItemResponse> items;
    private Long subtotalAmount;
    private Long discountAmount;
    private Long totalAmount;
    private String status;
}
