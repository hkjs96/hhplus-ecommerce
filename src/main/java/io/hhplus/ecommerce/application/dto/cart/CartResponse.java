package io.hhplus.ecommerce.application.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CartResponse {
    private String userId;
    private List<CartItemResponse> items;
    private Long totalAmount;
}
