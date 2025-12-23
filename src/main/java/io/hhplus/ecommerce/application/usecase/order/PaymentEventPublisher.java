package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;

/**
 * 결제 완료 이벤트 퍼블리셔 추상화
 * - 테스트에서 손쉽게 mock/verify 가능하도록 분리
 */
public interface PaymentEventPublisher {

    void publish(PaymentCompletedEvent event);
}
