package io.hhplus.ecommerce.infrastructure.kafka.message;

import io.hhplus.ecommerce.domain.order.Order;

import java.time.LocalDateTime;

/**
 * Kafka 주문 완료 메시지 DTO
 * - Kafka Topic: order-completed
 * - Producer: OrderEventProducer
 * - Consumer: OrderEventConsumer
 */
public record OrderCompletedMessage(
    Long orderId,
    Long userId,
    Long totalAmount,
    LocalDateTime completedAt
) {
    /**
     * Order 엔티티로부터 OrderCompletedMessage를 생성합니다.
     *
     * @param order 완료된 주문 엔티티
     * @return OrderCompletedMessage
     */
    public static OrderCompletedMessage from(Order order) {
        return new OrderCompletedMessage(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getPaidAt()
        );
    }
}
