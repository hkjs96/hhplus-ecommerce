package io.hhplus.ecommerce.application.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
    @NotNull(message = "사용자 ID는 필수입니다")
    Long userId,

    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다")
    @Valid
    List<OrderItemRequest> items,

    Long couponId
) {}
