package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 성공 - 금액 계산 검증")
    void create_성공() {
        // Given
        Long userId = 1L;
        Long subtotal = 100000L;
        Long discount = 10000L;

        // When
        Order order = Order.create("ORDER-001", userId, subtotal, discount);

        // Then
        assertThat(order.getSubtotalAmount()).isEqualTo(100000L);
        assertThat(order.getDiscountAmount()).isEqualTo(10000L);
        assertThat(order.getTotalAmount()).isEqualTo(90000L);  // subtotal - discount
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("주문 생성 성공 - 할인 없음")
    void create_할인없음_성공() {
        // Given
        Long userId = 1L;
        Long subtotal = 100000L;
        Long discount = 0L;

        // When
        Order order = Order.create("ORDER-001", userId, subtotal, discount);

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(100000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 생성 실패 - 할인 금액이 주문 금액보다 큼")
    void create_할인금액초과_예외발생() {
        // Given
        Long userId = 1L;
        Long subtotal = 100000L;
        Long discount = 150000L;  // 할인이 주문 금액보다 큼

        // When & Then
        assertThatThrownBy(() -> Order.create("ORDER-001", userId, subtotal, discount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 금액이 0")
    void create_주문금액0_예외발생() {
        // Given
        Long userId = 1L;
        Long subtotal = 0L;
        Long discount = 0L;

        // When & Then
        assertThatThrownBy(() -> Order.create("ORDER-001", userId, subtotal, discount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("주문 생성 실패 - 할인 금액이 음수")
    void create_할인금액음수_예외발생() {
        // Given
        Long userId = 1L;
        Long subtotal = 100000L;
        Long discount = -10000L;

        // When & Then
        assertThatThrownBy(() -> Order.create("ORDER-001", userId, subtotal, discount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("결제 완료 처리 성공 - PENDING에서 COMPLETED로 상태 전이")
    void complete_성공() {
        // Given
        Long userId = 1L;
        Order order = Order.create("ORDER-001", userId, 100000L, 10000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaidAt()).isNull();

        // When
        order.complete();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 완료 처리 실패 - PENDING이 아닌 상태")
    void complete_PENDING아님_예외발생() {
        // Given
        Long userId = 1L;
        Order order = Order.create("ORDER-001", userId, 100000L, 10000L);
        order.complete();  // 먼저 완료 처리
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // When & Then (이미 완료된 주문을 다시 완료하려 시도)
        assertThatThrownBy(() -> order.complete())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("주문 취소 처리 성공 - PENDING에서 CANCELLED로 상태 전이")
    void cancel_성공() {
        // Given
        Long userId = 1L;
        Order order = Order.create("ORDER-001", userId, 100000L, 10000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // When
        order.cancel();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("주문 취소 처리 실패 - PENDING이 아닌 상태")
    void cancel_PENDING아님_예외발생() {
        // Given
        Long userId = 1L;
        Order order = Order.create("ORDER-001", userId, 100000L, 10000L);
        order.complete();  // 먼저 완료 처리
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // When & Then (완료된 주문을 취소하려 시도)
        assertThatThrownBy(() -> order.cancel())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("주문 상태 확인 메서드")
    void 상태확인메서드() {
        // Given
        Long userId = 1L;
        Order pendingOrder = Order.create("ORDER-001", userId, 100000L, 10000L);

        // When & Then - PENDING 상태
        assertThat(pendingOrder.isPending()).isTrue();
        assertThat(pendingOrder.isCompleted()).isFalse();
        assertThat(pendingOrder.isCancelled()).isFalse();

        // When - COMPLETED 상태로 전이
        pendingOrder.complete();

        // Then - COMPLETED 상태
        assertThat(pendingOrder.isPending()).isFalse();
        assertThat(pendingOrder.isCompleted()).isTrue();
        assertThat(pendingOrder.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 금액이 null")
    void create_주문금액null_예외발생() {
        // Given
        Long userId = 1L;
        Long subtotal = null;
        Long discount = 0L;

        // When & Then
        assertThatThrownBy(() -> Order.create("ORDER-001", userId, subtotal, discount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("주문 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 금액이 음수")
    void create_주문금액음수_예외발생() {
        // Given
        Long userId = 1L;
        Long subtotal = -100000L;
        Long discount = 0L;

        // When & Then
        assertThatThrownBy(() -> Order.create("ORDER-001", userId, subtotal, discount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("주문 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("주문 생성 실패 - 할인 금액이 null")
    void create_할인금액null_예외발생() {
        // Given
        Long userId = 1L;
        Long subtotal = 100000L;
        Long discount = null;

        // When & Then
        assertThatThrownBy(() -> Order.create("ORDER-001", userId, subtotal, discount))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("할인 금액은 필수입니다");
    }

    @Test
    @DisplayName("주문 취소 후 완료 시도 실패")
    void cancel_후_complete_예외발생() {
        // Given
        Long userId = 1L;
        Order order = Order.create("ORDER-001", userId, 100000L, 10000L);
        order.cancel();  // 먼저 취소 처리
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // When & Then (취소된 주문을 완료하려 시도)
        assertThatThrownBy(() -> order.complete())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
            .hasMessageContaining("결제 대기 중인 주문만 완료할 수 있습니다");
    }

    @Test
    @DisplayName("취소된 주문 상태 확인")
    void 취소된주문_상태확인() {
        // Given
        Long userId = 1L;
        Order order = Order.create("ORDER-001", userId, 100000L, 10000L);

        // When
        order.cancel();

        // Then
        assertThat(order.isPending()).isFalse();
        assertThat(order.isCompleted()).isFalse();
        assertThat(order.isCancelled()).isTrue();
    }
}
