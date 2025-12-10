package io.hhplus.ecommerce.application.payment.listener;

import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class PaymentNotificationListener {

    @Async
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("결제 완료 알림 발송 시작: userId={}, orderId={}", event.userId(), event.orderId());
        try {
            // 외부 알림 서비스(SMS, 이메일 등)로 발송하는 로직 (Mocking)
            Thread.sleep(500); // 0.5초 지연 시뮬레이션
            log.info("결제 완료 알림 발송 성공: userId={}, orderId={}", event.userId(), event.orderId());
        } catch (InterruptedException e) {
            log.error("알림 발송 중 스레드 인터럽트 발생: orderId={}", event.orderId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("결제 완료 알림 발송 실패: orderId={}", event.orderId(), e);
            // 알림 실패는 치명적이지 않으므로, 재시도 없이 로그만 남기고 넘어갈 수 있음
        }
    }
}
