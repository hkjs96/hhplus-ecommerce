package io.hhplus.ecommerce.infrastructure.kafka.message;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCompletedMessage 테스트")
class OrderCompletedMessageTest {

    @Test
    @DisplayName("Order 엔티티로부터 OrderCompletedMessage를 생성한다")
    void fromOrder() {
        // given
        User user = User.createForTest(1L, "test@example.com", "테스트유저", 100000L);
        Order order = Order.create("ORD-20250120-001", user, 50000L, 5000L);
        order.complete(); // paidAt 설정

        // when
        OrderCompletedMessage message = OrderCompletedMessage.from(order);

        // then
        assertThat(message.orderId()).isEqualTo(order.getId());
        assertThat(message.userId()).isEqualTo(1L);
        assertThat(message.totalAmount()).isEqualTo(45000L); // 50000 - 5000
        assertThat(message.completedAt()).isNotNull();
        assertThat(message.completedAt()).isEqualTo(order.getPaidAt());
    }

    @Test
    @DisplayName("필드 값이 정확히 매핑된다")
    void fieldMapping() {
        // given
        User user = User.createForTest(2L, "test@example.com", "테스트유저", 100000L);
        Order order = Order.create("ORD-20250120-002", user, 100000L, 10000L);
        order.complete();

        // when
        OrderCompletedMessage message = OrderCompletedMessage.from(order);

        // then
        assertThat(message.totalAmount()).isEqualTo(90000L); // 100000 - 10000
    }
}
