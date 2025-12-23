package io.hhplus.ecommerce.infrastructure.kafka.consumer;

import io.hhplus.ecommerce.application.usecase.coupon.IssueCouponActualService;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
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

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueRequestedConsumer {

    private static final Duration ISSUED_TTL = Duration.ofDays(365);

    private final IssueCouponActualService issueCouponActualService;
    private final CouponIssueReservationStore couponIssueReservationStore;

    @KafkaListener(
        topics = "coupon-issue-requested",
        groupId = "coupon-issuer",
        containerFactory = "couponIssueKafkaListenerContainerFactory"
    )
    public void consume(
        @Payload CouponIssueRequestedMessage message,
        Acknowledgment ack,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
    ) {
        log.info("Coupon issue requested: couponId={}, userId={}, requestId={}, partition={}",
            message.couponId(), message.userId(), message.requestId(), partition);

        try {
            issueCouponActualService.issueActual(message.couponId(), message.userId());
            couponIssueReservationStore.confirmIssued(message.couponId(), message.userId(), ISSUED_TTL);
            ack.acknowledge();

        } catch (BusinessException e) {
            // 비즈니스 실패는 재시도하지 않고 종료/보상
            if (e.getErrorCode() == ErrorCode.COUPON_SOLD_OUT) {
                couponIssueReservationStore.cancelReservation(message.couponId(), message.userId());
                ack.acknowledge();
                return;
            }
            if (e.getErrorCode() == ErrorCode.ALREADY_ISSUED_COUPON) {
                // 멱등 케이스: 이미 발급된 경우 remaining을 복구하면 중복 보상이 될 수 있으므로 issued 확정 처리
                couponIssueReservationStore.confirmIssued(message.couponId(), message.userId(), ISSUED_TTL);
                ack.acknowledge();
                return;
            }

            // 그 외는 재시도/DLT로 위임
            throw e;
        }
    }
}
