package io.hhplus.ecommerce.application.facade;

import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.application.usecase.order.CreateOrderUseCase;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final CreateOrderUseCase createOrderUseCase;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final OrderRepository orderRepository;

    public CompleteOrderResponse createAndPayOrder(CompleteOrderRequest request) {
        log.info("Facade: Create and pay order for user: {}", request.getUserId());

        // 1. 주문 생성
        CreateOrderRequest createRequest = CreateOrderRequest.builder()
                .userId(request.getUserId())
                .items(request.getItems())
                .couponId(request.getCouponId())
                .build();

        CreateOrderResponse order = createOrderUseCase.execute(createRequest);
        log.debug("Order created: {}", order.getOrderId());

        // 2. 즉시 결제
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .userId(request.getUserId())
                .build();

        PaymentResponse payment = processPaymentUseCase.execute(order.getOrderId(), paymentRequest);
        log.debug("Payment processed: {}", payment.getStatus());

        // 3. 결제 후 최신 주문 상태를 가진 응답 생성
        // CreateOrderResponse의 모든 필드를 유지하되, status만 업데이트
        Order updatedOrder = orderRepository.findByIdOrThrow(order.getOrderId());
        CreateOrderResponse updatedOrderResponse = CreateOrderResponse.of(
                order.getOrderId(),
                order.getUserId(),
                order.getItems(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                updatedOrder.getStatus().name(),  // 업데이트된 status (COMPLETED)
                order.getCreatedAt()
        );

        // 4. 통합 응답
        return CompleteOrderResponse.builder()
                .order(updatedOrderResponse)
                .payment(payment)
                .build();
    }
}

