package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderItemTest {

    @Test
    @DisplayName("주문 상품 생성 성공 - 소계 계산 검증")
    void create_성공() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = 3;
        Long unitPrice = 50000L;

        // When
        OrderItem orderItem = OrderItem.create(orderId, productId, quantity, unitPrice);

        // Then
        assertThat(orderItem.getId()).isNull(); // ID는 JPA에 의해 자동 생성됨
        assertThat(orderItem.getOrderId()).isEqualTo(orderId);
        assertThat(orderItem.getProductId()).isEqualTo(productId);
        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getUnitPrice()).isEqualTo(50000L);
        assertThat(orderItem.getSubtotal()).isEqualTo(150000L);  // 50000 * 3
    }

    @Test
    @DisplayName("주문 상품 생성 실패 - 수량이 null")
    void create_수량null_예외발생() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = null;
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(orderId, productId, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("주문 상품 생성 실패 - 수량이 0")
    void create_수량0_예외발생() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = 0;
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(orderId, productId, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("주문 상품 생성 실패 - 수량이 음수")
    void create_수량음수_예외발생() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = -3;
        Long unitPrice = 50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(orderId, productId, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("주문 상품 생성 실패 - 단가가 null")
    void create_단가null_예외발생() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = 3;
        Long unitPrice = null;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(orderId, productId, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("상품 가격은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("주문 상품 생성 실패 - 단가가 0")
    void create_단가0_예외발생() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = 3;
        Long unitPrice = 0L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(orderId, productId, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("상품 가격은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("주문 상품 생성 실패 - 단가가 음수")
    void create_단가음수_예외발생() {
        // Given
        Long orderId = 1L;
        Long productId = 2L;
        Integer quantity = 3;
        Long unitPrice = -50000L;

        // When & Then
        assertThatThrownBy(() -> OrderItem.create(orderId, productId, quantity, unitPrice))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("상품 가격은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("주문 상품 소계 계산 검증 - 다양한 수량")
    void 소계계산_검증() {
        // Given & When & Then
        OrderItem item1 = OrderItem.create(1L, 2L, 1, 50000L);
        assertThat(item1.getSubtotal()).isEqualTo(50000L);

        OrderItem item2 = OrderItem.create(1L, 3L, 5, 10000L);
        assertThat(item2.getSubtotal()).isEqualTo(50000L);

        OrderItem item3 = OrderItem.create(1L, 4L, 10, 3500L);
        assertThat(item3.getSubtotal()).isEqualTo(35000L);
    }
}
