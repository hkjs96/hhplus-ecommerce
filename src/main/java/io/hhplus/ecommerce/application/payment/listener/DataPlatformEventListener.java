package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class DataPlatformEventListener {

    @Async
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("데이터 플랫폼 전송 시작: orderId={}", event.getOrder().getId());
        try {
            // 외부 데이터 플랫폼으로 전송하는 로직 (Mocking)
            Thread.sleep(1000); // 1초 지연 시뮬레이션
            log.info("데이터 플랫폼 전송 성공: orderId={}", event.getOrder().getId());
        } catch (InterruptedException e) {
            log.error("데이터 플랫폼 전송 중 스레드 인터럽트 발생: orderId={}", event.getOrder().getId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 실패: orderId={}", event.getOrder().getId(), e);
            // 실제로는 재시도 로직 또는 Dead Letter Queue(DLQ)에 적재하여 처리해야 함
        }
    }
}
