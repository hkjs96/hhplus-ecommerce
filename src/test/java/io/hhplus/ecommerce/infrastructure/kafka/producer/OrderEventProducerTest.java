package io.hhplus.ecommerce.infrastructure.kafka.producer;

import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventProducer 테스트")
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderEventProducer orderEventProducer;

    @BeforeEach
    void setUp() {
        orderEventProducer = new OrderEventProducer(kafkaTemplate);
    }

    @Test
    @DisplayName("주문 완료 메시지를 Kafka에 발행한다")
    void publishOrderCompleted() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            1L,
            100L,
            50000L,
            LocalDateTime.now()
        );

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(eq("order-completed"), any(OrderCompletedMessage.class)))
            .thenReturn(future);

        // when
        orderEventProducer.publishOrderCompleted(message);

        // then
        verify(kafkaTemplate, times(1)).send(eq("order-completed"), any(OrderCompletedMessage.class));
    }

    @Test
    @DisplayName("메시지 발행 시 KafkaTemplate을 사용한다")
    void usesKafkaTemplate() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            2L,
            200L,
            100000L,
            LocalDateTime.now()
        );

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), any(OrderCompletedMessage.class)))
            .thenReturn(future);

        // when
        orderEventProducer.publishOrderCompleted(message);

        // then
        assertThat(orderEventProducer).isNotNull();
        verify(kafkaTemplate).send(eq("order-completed"), eq(message));
    }
}
