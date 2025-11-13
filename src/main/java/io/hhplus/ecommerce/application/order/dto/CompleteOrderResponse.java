package io.hhplus.ecommerce.application.order.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompleteOrderResponse {
    private CreateOrderResponse order;
    private PaymentResponse payment;
}
