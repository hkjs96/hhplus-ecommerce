package io.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class CreateOrderResponse {
    private String orderId;
    private String userId;
    private List<OrderItemResponse> items;
    private Long subtotalAmount;
    private Long discountAmount;
    private Long totalAmount;
    private String status;
    private LocalDateTime createdAt;

    public static CreateOrderResponse of(
            String orderId,
            String userId,
            List<OrderItemResponse> items,
            Long subtotalAmount,
            Long discountAmount,
            Long totalAmount,
            String status,
            LocalDateTime createdAt
    ) {
        return new CreateOrderResponse(
                orderId,
                userId,
                items,
                subtotalAmount,
                discountAmount,
                totalAmount,
                status,
                createdAt
        );
    }
}
