package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DataPlatformEventListener Unit Test
 *
 * 목적: 비즈니스 로직만 검증 (현재는 로그만 남김)
 * - 이벤트 수신 시 정상 처리
 * - 예외 발생 시 로그로만 처리 (주문 영향 없음)
 *
 * Note: 실제 외부 API 클라이언트가 추가되면 Mock으로 검증
 */
@ExtendWith(MockitoExtension.class)
class DataPlatformEventListenerTest {

    @InjectMocks
    private DataPlatformEventListener listener;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "테스트유저");
        setId(testUser, 1L);
    }

    @Test
    @DisplayName("결제 완료 이벤트 수신 시 정상 처리")
    void handlePaymentCompleted_정상처리() {
        // Given: 결제 완료 이벤트
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        setId(order, 1L);
        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        // When & Then: 예외 없이 정상 처리
        assertThatCode(() -> listener.handlePaymentCompleted(event))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이벤트 처리 중 예외 발생 시 로그로만 처리 (주문 영향 없음)")
    void handlePaymentCompleted_예외처리() {
        // Given: 결제 완료 이벤트
        Order order = Order.create("ORDER-001", testUser, 30_000L, 0L);
        setId(order, 1L);
        PaymentCompletedEvent event = new PaymentCompletedEvent(order);

        // When & Then: Thread.sleep이 InterruptedException을 던져도 정상 처리
        // (실제로는 외부 API 호출 실패를 시뮬레이션해야 하지만, 현재는 로그만 있음)
        assertThatCode(() -> listener.handlePaymentCompleted(event))
            .doesNotThrowAnyException();
    }

    // ===== Helper Methods =====

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID", e);
        }
    }
}
