package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderItemTest {

    private Order mockOrder;
    private Product mockProduct;

    @BeforeEach
    void setUp() {
        // Mock 객체 생성
        mockOrder = mock(Order.class);
        mockProduct = mock(Product.class);

        // Mock 동작 설정
        when(mockOrder.getId()).thenReturn(1L);
        when(mockProduct.getId()).thenReturn(2L);
    }

    @Test
    @DisplayName("주문 항목 생성 - 성공")
    void create_성공() {
        // Given
        Integer quantity = 3;
        Long unitPrice = 50000L;

        // When
        OrderItem orderItem = OrderItem.create(mockOrder, mockProduct, quantity, unitPrice);

        // Then
        assertThat(orderItem).isNotNull();
        assertThat(orderItem.getId()).isNull(); // ID는 JPA에 의해 자동 생성됨
        assertThat(orderItem.getOrderId()).isEqualTo(1L);
        assertThat(orderItem.getProductId()).isEqualTo(2L);
        assertThat(orderItem.getQuantity()).isEqualTo(quantity);
        assertThat(orderItem.getUnitPrice()).isEqualTo(unitPrice);
        assertThat(orderItem.getSubtotal()).isEqualTo(150000L); // 50000 * 3
    }

    @Test
    @DisplayName("주문 항목 생성 - order null")
    void create_실패_order_null() {
        // Given
        Integer quantity = 3;
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(null, mockProduct, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("주문은 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("주문 항목 생성 - product null")
    void create_실패_product_null() {
        // Given
        Integer quantity = 3;
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(mockOrder, null, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("상품은 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("주문 항목 생성 - quantity null")
    void create_실패_quantity_null() {
        // Given
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(mockOrder, mockProduct, null, unitPrice))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("주문 항목 생성 - quantity 0")
    void create_실패_quantity_0() {
        // Given
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(mockOrder, mockProduct, 0, unitPrice))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("주문 항목 생성 - quantity 음수")
    void create_실패_quantity_음수() {
        // Given
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(mockOrder, mockProduct, -1, unitPrice))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("주문 항목 생성 - unitPrice null")
    void create_실패_unitPrice_null() {
        // Given
        Integer quantity = 3;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(mockOrder, mockProduct, quantity, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("상품 가격은 0보다 커야 합니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("주문 항목 생성 - unitPrice 음수")
    void create_실패_unitPrice_음수() {
        // Given
        Integer quantity = 3;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(mockOrder, mockProduct, quantity, -1000L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("상품 가격은 0보다 커야 합니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("소계 계산 - 성공")
    void calculateSubtotal_성공() {
        // Given & When
        OrderItem item1 = OrderItem.create(mockOrder, mockProduct, 1, 50000L);
        OrderItem item2 = OrderItem.create(mockOrder, mockProduct, 5, 10000L);
        OrderItem item3 = OrderItem.create(mockOrder, mockProduct, 10, 3500L);

        // Then
        assertThat(item1.getSubtotal()).isEqualTo(50000L);  // 1 * 50000
        assertThat(item2.getSubtotal()).isEqualTo(50000L);  // 5 * 10000
        assertThat(item3.getSubtotal()).isEqualTo(35000L);  // 10 * 3500
    }
}
