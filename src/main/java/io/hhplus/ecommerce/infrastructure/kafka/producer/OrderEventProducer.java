package io.hhplus.ecommerce.infrastructure.kafka.producer;

import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final String ORDER_COMPLETED_TOPIC = "order-completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCompleted(OrderCompletedMessage message) {
        kafkaTemplate.send(ORDER_COMPLETED_TOPIC, message)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    var metadata = result.getRecordMetadata();
                    log.info("Kafka message published: orderId={}, topic={}, partition={}, offset={}",
                        message.orderId(),
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset()
                    );
                } else {
                    log.error("Failed to publish Kafka message: orderId={}, error={}",
                        message.orderId(),
                        ex.getMessage(),
                        ex
                    );
                    throw new RuntimeException("Kafka 메시지 발행 실패", ex);
                }
            });
    }
}
