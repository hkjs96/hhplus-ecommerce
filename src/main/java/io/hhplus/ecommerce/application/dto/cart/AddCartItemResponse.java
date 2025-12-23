package io.hhplus.ecommerce.application.dto.cart;

import java.time.LocalDateTime;

public record AddCartItemResponse(
    String cartItemId,
    String productId,
    Integer quantity,
    LocalDateTime addedAt
) {
}
