package io.hhplus.ecommerce.application.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AddCartItemResponse {
    private String cartItemId;
    private String productId;
    private Integer quantity;
    private LocalDateTime addedAt;
}
