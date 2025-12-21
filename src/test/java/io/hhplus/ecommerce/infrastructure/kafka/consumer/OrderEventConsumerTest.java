package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer 테스트")
class OrderEventConsumerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Acknowledgment acknowledgment;

    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        orderEventConsumer = new OrderEventConsumer(redisTemplate);
    }

    @Test
    @DisplayName("새 메시지를 처음 처리하면 비즈니스 로직 실행 후 ACK")
    void consumeNewMessage() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            1L,
            100L,
            50000L,
            LocalDateTime.now()
        );
        int partition = 0;

        // Redis SETNX 성공 (처음 처리)
        when(valueOperations.setIfAbsent(
            eq("kafka:processed:order:1"),
            eq("1"),
            any(Duration.class)
        )).thenReturn(true);

        // when
        orderEventConsumer.consumeOrderCompleted(message, acknowledgment, partition);

        // then
        verify(valueOperations, times(1)).setIfAbsent(
            eq("kafka:processed:order:1"),
            eq("1"),
            eq(Duration.ofDays(7))
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("중복 메시지는 비즈니스 로직 실행하지 않고 즉시 ACK")
    void consumeDuplicateMessage() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            2L,
            200L,
            100000L,
            LocalDateTime.now()
        );
        int partition = 1;

        // Redis SETNX 실패 (이미 처리됨)
        when(valueOperations.setIfAbsent(
            eq("kafka:processed:order:2"),
            eq("1"),
            any(Duration.class)
        )).thenReturn(false);

        // when
        orderEventConsumer.consumeOrderCompleted(message, acknowledgment, partition);

        // then
        verify(valueOperations, times(1)).setIfAbsent(
            eq("kafka:processed:order:2"),
            eq("1"),
            eq(Duration.ofDays(7))
        );
        // 중복 메시지도 ACK는 해야 함 (재처리 방지)
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("메시지 수신 시 로깅을 위한 메타데이터 확인")
    void consumeMessageWithMetadata() {
        // given
        OrderCompletedMessage message = new OrderCompletedMessage(
            3L,
            300L,
            150000L,
            LocalDateTime.now()
        );
        int partition = 2;

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);

        // when
        orderEventConsumer.consumeOrderCompleted(message, acknowledgment, partition);

        // then
        verify(acknowledgment, times(1)).acknowledge();
    }
}
