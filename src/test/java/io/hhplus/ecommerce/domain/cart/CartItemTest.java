package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CartItemTest {

    @Test
    @DisplayName("장바구니 항목 생성 - 성공")
    void create_성공() {
        // Given
        String itemId = "ITEM-001";
        String cartId = "CART-U001";
        String productId = "P001";
        Integer quantity = 5;

        // When
        CartItem cartItem = CartItem.create(itemId, cartId, productId, quantity);

        // Then
        assertThat(cartItem).isNotNull();
        assertThat(cartItem.getId()).isEqualTo(itemId);
        assertThat(cartItem.getCartId()).isEqualTo(cartId);
        assertThat(cartItem.getProductId()).isEqualTo(productId);
        assertThat(cartItem.getQuantity()).isEqualTo(quantity);
        assertThat(cartItem.getAddedAt()).isNotNull();
    }

    @Test
    @DisplayName("장바구니 항목 생성 - cartId null")
    void create_실패_cartId_null() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create("ITEM-001", null, "P001", 5))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("장바구니 ID는 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - productId null")
    void create_실패_productId_null() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create("ITEM-001", "CART-U001", null, 5))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("상품 ID는 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - quantity null")
    void create_실패_quantity_null() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create("ITEM-001", "CART-U001", "P001", null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - quantity 0")
    void create_실패_quantity_0() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create("ITEM-001", "CART-U001", "P001", 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - quantity 음수")
    void create_실패_quantity_음수() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create("ITEM-001", "CART-U001", "P001", -1))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 변경 - 성공")
    void updateQuantity_성공() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        // When
        cartItem.updateQuantity(10);

        // Then
        assertThat(cartItem.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("수량 변경 - quantity 0")
    void updateQuantity_실패_quantity_0() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.updateQuantity(0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 변경 - quantity 음수")
    void updateQuantity_실패_quantity_음수() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.updateQuantity(-5))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 증가 - 성공")
    void increaseQuantity_성공() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        // When
        cartItem.increaseQuantity(3);

        // Then
        assertThat(cartItem.getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("수량 증가 - quantity 0")
    void increaseQuantity_실패_quantity_0() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.increaseQuantity(0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 증가 - quantity 음수")
    void increaseQuantity_실패_quantity_음수() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.increaseQuantity(-3))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }
}
