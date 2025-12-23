package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import io.hhplus.ecommerce.infrastructure.kafka.message.CouponIssueRequestedMessage;
import io.hhplus.ecommerce.infrastructure.redis.CouponIssueReservationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueRequestedDltConsumer {

    private final CouponIssueReservationStore couponIssueReservationStore;

    @KafkaListener(
        topics = "coupon-issue-requested.DLT",
        groupId = "coupon-issuer-dlt",
        containerFactory = "couponIssueKafkaListenerContainerFactory"
    )
    public void consume(
        @Payload CouponIssueRequestedMessage message,
        Acknowledgment ack,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
    ) {
        log.warn("Coupon issue moved to DLT, compensating: couponId={}, userId={}, requestId={}, partition={}",
            message.couponId(), message.userId(), message.requestId(), partition);

        boolean compensated = couponIssueReservationStore.compensateReservation(message.couponId(), message.userId());
        log.warn("DLT compensation done: couponId={}, userId={}, compensated={}",
            message.couponId(), message.userId(), compensated);

        ack.acknowledge();
    }
}

