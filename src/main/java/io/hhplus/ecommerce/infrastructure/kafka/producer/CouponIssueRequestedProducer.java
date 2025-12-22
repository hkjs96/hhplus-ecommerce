package io.hhplus.ecommerce.infrastructure.kafka.producer;

import io.hhplus.ecommerce.infrastructure.kafka.message.CouponIssueRequestedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueRequestedProducer {

    public static final String TOPIC = "coupon-issue-requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(CouponIssueRequestedMessage message) {
        // Partition key: couponId (동일 쿠폰 흐름의 순서 보장/병렬 처리 단위)
        String key = String.valueOf(message.couponId());

        kafkaTemplate.send(TOPIC, key, message)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    var metadata = result.getRecordMetadata();
                    log.info("Kafka message published: topic={}, partition={}, offset={}, couponId={}, userId={}, requestId={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                        message.couponId(),
                        message.userId(),
                        message.requestId()
                    );
                } else {
                    log.error("Failed to publish Kafka message: couponId={}, userId={}, requestId={}, error={}",
                        message.couponId(),
                        message.userId(),
                        message.requestId(),
                        ex.getMessage(),
                        ex
                    );
                }
            });
    }
}

