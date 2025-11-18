package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartItemTest {

    private Cart mockCart;
    private Product mockProduct;

    @BeforeEach
    void setUp() {
        // Mock 객체 생성
        mockCart = mock(Cart.class);
        mockProduct = mock(Product.class);

        // Mock 동작 설정
        when(mockCart.getId()).thenReturn(1L);
        when(mockProduct.getId()).thenReturn(2L);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - 성공")
    void create_성공() {
        // Given
        Integer quantity = 5;

        // When
        CartItem cartItem = CartItem.create(mockCart, mockProduct, quantity);

        // Then
        assertThat(cartItem).isNotNull();
        assertThat(cartItem.getId()).isNull(); // ID는 JPA에 의해 자동 생성됨
        assertThat(cartItem.getCartId()).isEqualTo(1L);
        assertThat(cartItem.getProductId()).isEqualTo(2L);
        assertThat(cartItem.getQuantity()).isEqualTo(quantity);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - cart null")
    void create_실패_cart_null() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create(null, mockProduct, 5))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("장바구니는 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - product null")
    void create_실패_product_null() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create(mockCart, null, 5))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("상품은 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - quantity null")
    void create_실패_quantity_null() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create(mockCart, mockProduct, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - quantity 0")
    void create_실패_quantity_0() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create(mockCart, mockProduct, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("장바구니 항목 생성 - quantity 음수")
    void create_실패_quantity_음수() {
        // When & Then
        assertThatThrownBy(() -> CartItem.create(mockCart, mockProduct, -1))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 업데이트 - 성공")
    void updateQuantity_성공() {
        // Given
        CartItem cartItem = CartItem.create(mockCart, mockProduct, 5);
        Integer newQuantity = 10;

        // When
        cartItem.updateQuantity(newQuantity);

        // Then
        assertThat(cartItem.getQuantity()).isEqualTo(newQuantity);
    }

    @Test
    @DisplayName("수량 업데이트 - null")
    void updateQuantity_실패_null() {
        // Given
        CartItem cartItem = CartItem.create(mockCart, mockProduct, 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.updateQuantity(null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 업데이트 - 0")
    void updateQuantity_실패_0() {
        // Given
        CartItem cartItem = CartItem.create(mockCart, mockProduct, 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.updateQuantity(0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 업데이트 - 음수")
    void updateQuantity_실패_음수() {
        // Given
        CartItem cartItem = CartItem.create(mockCart, mockProduct, 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.updateQuantity(-1))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 증가 - 성공")
    void increaseQuantity_성공() {
        // Given
        CartItem cartItem = CartItem.create(mockCart, mockProduct, 5);
        Integer additionalQuantity = 3;

        // When
        cartItem.increaseQuantity(additionalQuantity);

        // Then
        assertThat(cartItem.getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("수량 증가 - null")
    void increaseQuantity_실패_null() {
        // Given
        CartItem cartItem = CartItem.create(mockCart, mockProduct, 5);

        // When & Then
        assertThatThrownBy(() -> cartItem.increaseQuantity(null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }
}
