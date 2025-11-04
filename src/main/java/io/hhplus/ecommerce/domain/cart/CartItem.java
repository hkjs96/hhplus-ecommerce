package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 장바구니 항목 엔티티 (Rich Domain Model)
 * Week 3: Pure Java Entity (JPA 어노테이션 없음)
 *
 * 비즈니스 규칙:
 * - quantity는 1 이상이어야 함
 * - 같은 상품 중복 추가 시 수량만 증가
 */
@Getter
@AllArgsConstructor
public class CartItem {

    private String id;
    private String cartId;
    private String productId;
    private Integer quantity;
    private LocalDateTime addedAt;

    /**
     * 장바구니 항목 생성 (Factory Method)
     */
    public static CartItem create(String id, String cartId, String productId, Integer quantity) {
        validateCartId(cartId);
        validateProductId(productId);
        validateQuantity(quantity);

        LocalDateTime now = LocalDateTime.now();
        return new CartItem(id, cartId, productId, quantity, now);
    }

    /**
     * 수량 변경 (비즈니스 로직)
     *
     * @param quantity 새로운 수량
     * @throws BusinessException 수량이 0 이하인 경우
     */
    public void updateQuantity(Integer quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    /**
     * 수량 증가
     *
     * @param additionalQuantity 추가 수량
     * @throws BusinessException 추가 수량이 0 이하인 경우
     */
    public void increaseQuantity(Integer additionalQuantity) {
        validateQuantity(additionalQuantity);
        this.quantity += additionalQuantity;
    }

    // ====================================
    // Validation Methods
    // ====================================

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
