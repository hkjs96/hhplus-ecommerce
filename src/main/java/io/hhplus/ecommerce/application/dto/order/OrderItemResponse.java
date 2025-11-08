package io.hhplus.ecommerce.application.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderItemResponse {
    private String productId;
    private String name;
    private Integer quantity;
    private Long unitPrice;
    private Long subtotal;
}
