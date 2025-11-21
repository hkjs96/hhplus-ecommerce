package io.hhplus.ecommerce.application.facade;

import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.application.usecase.order.CreateOrderUseCase;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final CreateOrderUseCase createOrderUseCase;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final OrderRepository orderRepository;

    public CompleteOrderResponse createAndPayOrder(CompleteOrderRequest request) {
        log.info("Facade: Create and pay order for user: {}", request.userId());

        // 1. 주문 생성
        CreateOrderRequest createRequest = new CreateOrderRequest(
                request.userId(),
                request.items(),
                request.couponId()
        );

        CreateOrderResponse order = createOrderUseCase.execute(createRequest);
        log.debug("Order created: {}", order.orderId());

        // 2. 즉시 결제
        String idempotencyKey = "ORDER_" + order.orderId() + "_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(request.userId(), idempotencyKey);

        PaymentResponse payment = processPaymentUseCase.execute(order.orderId(), paymentRequest);
        log.debug("Payment processed: {}", payment.status());

        // 3. 결제 후 최신 주문 상태 반영
        Order updatedOrder = getUpdatedOrder(order.orderId());
        CreateOrderResponse updatedOrderResponse = new CreateOrderResponse(
                updatedOrder.getId(),
                updatedOrder.getUserId(),
                updatedOrder.getOrderNumber(),
                order.items(),  // items는 그대로 사용
                updatedOrder.getSubtotalAmount(),
                updatedOrder.getDiscountAmount(),
                updatedOrder.getTotalAmount(),
                updatedOrder.getStatus().name(),  // COMPLETED로 변경됨
                updatedOrder.getCreatedAt()
        );

        // 4. 통합 응답
        return new CompleteOrderResponse(updatedOrderResponse, payment);
    }

    @Transactional(readOnly = true)
    protected Order getUpdatedOrder(Long orderId) {
        return orderRepository.findByIdOrThrow(orderId);
    }
}

