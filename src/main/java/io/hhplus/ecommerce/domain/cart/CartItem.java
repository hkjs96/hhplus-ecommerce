package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CartItem {

    private String id;
    private String cartId;
    private String productId;
    private Integer quantity;
    private LocalDateTime addedAt;

    public static CartItem create(String id, String cartId, String productId, Integer quantity) {
        validateCartId(cartId);
        validateProductId(productId);
        validateQuantity(quantity);

        LocalDateTime now = LocalDateTime.now();
        return new CartItem(id, cartId, productId, quantity, now);
    }

    public void updateQuantity(Integer quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public void increaseQuantity(Integer additionalQuantity) {
        validateQuantity(additionalQuantity);
        this.quantity += additionalQuantity;
    }

    private static void validateCartId(String cartId) {
        if (cartId == null || cartId.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "장바구니 ID는 필수입니다"
            );
        }
    }

    private static void validateProductId(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품 ID는 필수입니다"
            );
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
