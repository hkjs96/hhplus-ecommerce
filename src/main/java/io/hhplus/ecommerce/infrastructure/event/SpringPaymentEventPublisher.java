package io.hhplus.ecommerce.infrastructure.event;

import io.hhplus.ecommerce.application.usecase.order.PaymentEventPublisher;
import io.hhplus.ecommerce.domain.order.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring ApplicationEventPublisher 기반 구현체
 */
@Component
@RequiredArgsConstructor
public class SpringPaymentEventPublisher implements PaymentEventPublisher {

    private final ApplicationEventPublisher delegate;

    @Override
    public void publish(PaymentCompletedEvent event) {
        delegate.publishEvent(event);
    }
}
