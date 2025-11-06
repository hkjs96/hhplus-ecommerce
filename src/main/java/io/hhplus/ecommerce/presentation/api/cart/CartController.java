package io.hhplus.ecommerce.presentation.api.cart;

import io.hhplus.ecommerce.application.cart.CartService;
import io.hhplus.ecommerce.application.cart.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
        @Valid @RequestBody AddCartItemRequest request
    ) {
        CartResponse response = cartService.addItemToCart(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
        @NotBlank(message = "사용자 ID는 필수입니다") @RequestParam String userId
    ) {
        CartResponse response = cartService.getCart(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/items")
    public ResponseEntity<CartItemResponse> updateItem(
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartItemResponse response = cartService.updateCartItem(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> deleteItem(
        @Valid @RequestBody DeleteCartItemRequest request
    ) {
        cartService.deleteCartItem(request);
        return ResponseEntity.ok().build();
    }
}
