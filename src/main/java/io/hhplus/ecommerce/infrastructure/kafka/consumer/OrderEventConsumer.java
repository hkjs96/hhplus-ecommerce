package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String PROCESSED_KEY_PREFIX = "kafka:processed:order:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(
        topics = "order-completed",
        groupId = "data-platform"
    )
    public void consumeOrderCompleted(
        @Payload OrderCompletedMessage message,
        Acknowledgment ack,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
    ) {
        log.info("Kafka message received: orderId={}, userId={}, totalAmount={}, partition={}",
            message.orderId(),
            message.userId(),
            message.totalAmount(),
            partition
        );

        String key = PROCESSED_KEY_PREFIX + message.orderId();

        // 멱등성 처리: Redis SETNX
        Boolean isFirstProcessing = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);

        if (Boolean.TRUE.equals(isFirstProcessing)) {
            // 처음 처리: 비즈니스 로직 실행
            processOrderCompletedEvent(message);
            log.info("Kafka message processed: orderId={}, partition={}", message.orderId(), partition);
        } else {
            // 중복 처리: 즉시 ACK (재처리 방지)
            log.info("Duplicate message ignored: orderId={}, partition={}", message.orderId(), partition);
        }

        // Manual ACK
        ack.acknowledge();
    }

    private void processOrderCompletedEvent(OrderCompletedMessage message) {
        // TODO: A6에서 실제 비즈니스 로직 구현 (데이터 플랫폼 전송 등)
        log.debug("Processing order completed event: orderId={}, userId={}, totalAmount={}",
            message.orderId(),
            message.userId(),
            message.totalAmount()
        );
    }
}
