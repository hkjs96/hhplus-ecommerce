package io.hhplus.ecommerce.presentation.api.cart;

import io.hhplus.ecommerce.application.cart.dto.*;
import io.hhplus.ecommerce.application.usecase.cart.AddToCartUseCase;
import io.hhplus.ecommerce.application.usecase.cart.GetCartUseCase;
import io.hhplus.ecommerce.application.usecase.cart.RemoveFromCartUseCase;
import io.hhplus.ecommerce.application.usecase.cart.UpdateCartItemUseCase;
import jakarta.validation.Valid;
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

    private final AddToCartUseCase addToCartUseCase;
    private final GetCartUseCase getCartUseCase;
    private final UpdateCartItemUseCase updateCartItemUseCase;
    private final RemoveFromCartUseCase removeFromCartUseCase;

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
        @Valid @RequestBody AddCartItemRequest request
    ) {
        CartResponse response = addToCartUseCase.execute(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
        @RequestParam Long userId
    ) {
        CartResponse response = getCartUseCase.execute(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/items")
    public ResponseEntity<CartItemResponse> updateItem(
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartItemResponse response = updateCartItemUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> deleteItem(
        @Valid @RequestBody DeleteCartItemRequest request
    ) {
        removeFromCartUseCase.execute(request);
        return ResponseEntity.ok().build();
    }
}
