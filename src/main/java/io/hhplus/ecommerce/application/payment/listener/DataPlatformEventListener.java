package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import io.hhplus.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import io.hhplus.ecommerce.infrastructure.kafka.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataPlatformEventListener {

    private final OrderEventProducer orderEventProducer;

    @Async
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2),
        retryFor = {RuntimeException.class}
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("데이터 플랫폼 전송 시작 (Kafka): orderId={}", event.getOrder().getId());

        try {
            // Kafka로 주문 완료 이벤트 발행
            OrderCompletedMessage message = OrderCompletedMessage.from(event.getOrder());
            orderEventProducer.publishOrderCompleted(message);

            log.info("Kafka 메시지 발행 성공: orderId={}", event.getOrder().getId());

            // 기존 로직 유지 (호환성)
            Thread.sleep(1000); // 1초 지연 시뮬레이션
            log.info("데이터 플랫폼 전송 성공: orderId={}", event.getOrder().getId());

        } catch (InterruptedException e) {
            log.error("데이터 플랫폼 전송 중 스레드 인터럽트 발생: orderId={}", event.getOrder().getId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Kafka 메시지 발행 실패 (재시도 예정): orderId={}, error={}",
                event.getOrder().getId(), e.getMessage());
            throw e; // Spring Retry가 재시도하도록 예외 전파
        }
    }

    @Recover
    public void recover(Exception e, PaymentCompletedEvent event) {
        log.error("Kafka 메시지 발행 최종 실패 (3회 재시도 후): orderId={}, error={}",
            event.getOrder().getId(), e.getMessage(), e);
        // TODO: Outbox 테이블에 저장하여 백그라운드 재전송 (향후 구현)
    }
}
