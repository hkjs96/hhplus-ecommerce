package io.hhplus.ecommerce.application.product.listener;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.infrastructure.redis.EventIdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * EventIdempotencyListener 단위 테스트
 *
 * 목적: Mock을 사용한 간단한 동작 검증
 * - 멱등성 체크 정상 동작
 * - 중복 이벤트 감지 시 예외 발생
 */
@ExtendWith(MockitoExtension.class)
class EventIdempotencyListenerTest {

    @Mock
    private EventIdempotencyService idempotencyService;

    @InjectMocks
    private EventIdempotencyListener listener;

    @Test
    @DisplayName("신규 이벤트는 멱등성 기록 성공")
    void 신규이벤트_멱등성기록_성공() throws Exception {
        // Given: 신규 이벤트 (아직 처리되지 않음)
        User user = User.create("test@example.com", "테스트");
        // Reflection으로 ID 설정 (단위 테스트용)
        java.lang.reflect.Field userIdField = User.class.getDeclaredField("id");
        userIdField.setAccessible(true);
        userIdField.set(user, 1L);

        Order order = Order.create("ORDER-001", user, 10_000L, 0L);

        // Order에도 ID 설정
        java.lang.reflect.Field orderIdField = Order.class.getDeclaredField("id");
        orderIdField.setAccessible(true);
        orderIdField.set(order, 123L);

        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        given(idempotencyService.isProcessed(anyString(), anyString())).willReturn(false);

        // When: 멱등성 체크
        listener.checkIdempotency(event);

        // Then: 처리 완료 기록
        verify(idempotencyService).isProcessed(eq("PaymentCompleted"), eq("order-" + order.getId()));
        verify(idempotencyService).markAsProcessed(eq("PaymentCompleted"), eq("order-" + order.getId()));
    }

    @Test
    @DisplayName("중복 이벤트는 DuplicateEventException 발생")
    void 중복이벤트_예외발생() throws Exception {
        // Given: 이미 처리된 이벤트
        User user = User.create("test@example.com", "테스트");
        // Reflection으로 ID 설정 (단위 테스트용)
        java.lang.reflect.Field userIdField = User.class.getDeclaredField("id");
        userIdField.setAccessible(true);
        userIdField.set(user, 2L);

        Order order = Order.create("ORDER-002", user, 20_000L, 0L);

        // Order에도 ID 설정
        java.lang.reflect.Field orderIdField = Order.class.getDeclaredField("id");
        orderIdField.setAccessible(true);
        orderIdField.set(order, 456L);

        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        given(idempotencyService.isProcessed(anyString(), anyString())).willReturn(true);

        // When & Then: 예외 발생
        assertThatThrownBy(() -> listener.checkIdempotency(event))
            .isInstanceOf(EventIdempotencyListener.DuplicateEventException.class)
            .hasMessageContaining("이미 처리된 이벤트입니다");
    }
}
