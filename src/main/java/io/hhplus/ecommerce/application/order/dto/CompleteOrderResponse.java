package io.hhplus.ecommerce.application.order.dto;

public record CompleteOrderResponse(
    CreateOrderResponse order,
    PaymentResponse payment
) {
}
