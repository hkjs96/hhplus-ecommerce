package io.hhplus.ecommerce.presentation.api.cart;

import io.hhplus.ecommerce.application.dto.cart.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.presentation.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cart")
@Tag(name = "2. 장바구니", description = "장바구니 관리 API")
public class CartController {

    // Mock 데이터 저장소
    private final Map<String, CartItemData> cartStore = new ConcurrentHashMap<>();
    private final Map<String, ProductInfo> productStore = new ConcurrentHashMap<>();

    public CartController() {
        // 상품 정보 초기화 (재고 확인용)
        productStore.put("P001", new ProductInfo("P001", "노트북", 890000L, 10));
        productStore.put("P002", new ProductInfo("P002", "키보드", 120000L, 50));
        productStore.put("P003", new ProductInfo("P003", "모니터", 450000L, 20));
        productStore.put("P004", new ProductInfo("P004", "마우스", 75000L, 100));
        productStore.put("P005", new ProductInfo("P005", "헤드셋", 180000L, 30));

        // 초기 장바구니 데이터
        cartStore.put("CI001", new CartItemData("CI001", "U001", "P001", 2, LocalDateTime.now()));
        cartStore.put("CI002", new CartItemData("CI002", "U001", "P002", 1, LocalDateTime.now()));
    }

    /**
     * 2.1 장바구니 추가
     * POST /cart/items
     */
    @Operation(
        summary = "장바구니에 상품 추가",
        description = "장바구니에 상품을 추가합니다. 이미 담긴 상품이면 수량이 증가합니다."
    )
    @PostMapping("/items")
    public ApiResponse<AddCartItemResponse> addCartItem(@Valid @RequestBody AddCartItemRequest request) {
        log.info("POST /cart/items - userId: {}, productId: {}, quantity: {}",
            request.getUserId(), request.getProductId(), request.getQuantity());

        // 상품 존재 확인
        ProductInfo product = productStore.get(request.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 재고 확인
        if (product.stock < request.getQuantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        // 이미 장바구니에 있는 상품인지 확인
        CartItemData existingItem = cartStore.values().stream()
            .filter(item -> item.userId.equals(request.getUserId())
                && item.productId.equals(request.getProductId()))
            .findFirst()
            .orElse(null);

        if (existingItem != null) {
            // 기존 수량에 추가
            existingItem.quantity += request.getQuantity();

            AddCartItemResponse response = new AddCartItemResponse(
                existingItem.cartItemId,
                existingItem.productId,
                existingItem.quantity,
                existingItem.addedAt
            );
            return ApiResponse.success(response);
        } else {
            // 새 장바구니 항목 생성
            String cartItemId = "CI" + UUID.randomUUID().toString().substring(0, 8);
            CartItemData newItem = new CartItemData(
                cartItemId,
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                LocalDateTime.now()
            );
            cartStore.put(cartItemId, newItem);

            AddCartItemResponse response = new AddCartItemResponse(
                cartItemId,
                newItem.productId,
                newItem.quantity,
                newItem.addedAt
            );
            return ApiResponse.success(response);
        }
    }

    /**
     * 2.2 장바구니 조회
     * GET /cart
     */
    @Operation(
        summary = "장바구니 조회",
        description = "사용자의 장바구니 내용을 조회합니다. 재고 가용 여부를 포함합니다."
    )
    @GetMapping
    public ApiResponse<CartResponse> getCart(
        @Parameter(description = "사용자 ID", required = true)
        @RequestParam String userId
    ) {
        log.info("GET /cart - userId: {}", userId);

        List<CartItemData> userCartItems = cartStore.values().stream()
            .filter(item -> item.userId.equals(userId))
            .collect(Collectors.toList());

        List<CartItemResponse> items = new ArrayList<>();
        long totalAmount = 0L;

        for (CartItemData cartItem : userCartItems) {
            ProductInfo product = productStore.get(cartItem.productId);
            if (product != null) {
                long subtotal = product.price * cartItem.quantity;
                boolean stockAvailable = product.stock >= cartItem.quantity;

                CartItemResponse itemResponse = new CartItemResponse(
                    cartItem.cartItemId,
                    cartItem.productId,
                    product.name,
                    product.price,
                    cartItem.quantity,
                    subtotal,
                    stockAvailable,
                    product.stock
                );
                items.add(itemResponse);
                totalAmount += subtotal;
            }
        }

        CartResponse response = new CartResponse(userId, items, totalAmount);
        return ApiResponse.success(response);
    }

    /**
     * 2.3 장바구니 수정
     * PUT /cart/items
     */
    @Operation(
        summary = "장바구니 수량 수정",
        description = "장바구니 항목의 수량을 변경합니다."
    )
    @PutMapping("/items")
    public ApiResponse<CartItemResponse> updateCartItem(@Valid @RequestBody UpdateCartItemRequest request) {
        log.info("PUT /cart/items - cartItemId: {}, quantity: {}", request.getCartItemId(), request.getQuantity());

        CartItemData cartItem = cartStore.get(request.getCartItemId());
        if (cartItem == null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        ProductInfo product = productStore.get(cartItem.productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 재고 확인
        if (product.stock < request.getQuantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        // 수량 업데이트
        cartItem.quantity = request.getQuantity();

        long subtotal = product.price * cartItem.quantity;
        CartItemResponse response = new CartItemResponse(
            cartItem.cartItemId,
            cartItem.productId,
            product.name,
            product.price,
            cartItem.quantity,
            subtotal,
            true,
            product.stock
        );

        return ApiResponse.success(response);
    }

    /**
     * 2.4 장바구니 삭제
     * DELETE /cart/items
     */
    @Operation(
        summary = "장바구니 항목 삭제",
        description = "장바구니에서 특정 항목을 삭제합니다."
    )
    @DeleteMapping("/items")
    public ApiResponse<Void> deleteCartItem(
        @Parameter(description = "장바구니 항목 ID", required = true)
        @RequestParam String cartItemId
    ) {
        log.info("DELETE /cart/items - cartItemId: {}", cartItemId);

        CartItemData removed = cartStore.remove(cartItemId);
        if (removed == null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        return ApiResponse.success(null);
    }

    // Mock 데이터 클래스
    private static class CartItemData {
        String cartItemId;
        String userId;
        String productId;
        Integer quantity;
        LocalDateTime addedAt;

        CartItemData(String cartItemId, String userId, String productId, Integer quantity, LocalDateTime addedAt) {
            this.cartItemId = cartItemId;
            this.userId = userId;
            this.productId = productId;
            this.quantity = quantity;
            this.addedAt = addedAt;
        }
    }

    private static class ProductInfo {
        String productId;
        String name;
        Long price;
        Integer stock;

        ProductInfo(String productId, String name, Long price, Integer stock) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }
}
