package io.hhplus.ecommerce.application.facade;

import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.application.usecase.order.CreateOrderUseCase;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final CreateOrderUseCase createOrderUseCase;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final OrderRepository orderRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${demo.payment.remote.enabled:false}")
    private boolean remotePaymentEnabled;

    @Value("${demo.payment.remote.base-url:}")
    private String remotePaymentBaseUrl;

    public CompleteOrderResponse createAndPayOrder(CompleteOrderRequest request) {
        log.info("Facade: Create and pay order for user: {}", request.userId());

        // 1. 주문 생성 (idempotencyKey 생성)
        String orderIdempotencyKey = "CREATE_ORDER_" + request.userId() + "_" + UUID.randomUUID().toString();
        CreateOrderRequest createRequest = new CreateOrderRequest(
                request.userId(),
                request.items(),
                request.couponId(),
                orderIdempotencyKey
        );

        CreateOrderResponse order = createOrderUseCase.execute(createRequest);
        log.debug("Order created: {}", order.orderId());

        // 2. 즉시 결제
        String idempotencyKey = "ORDER_" + order.orderId() + "_" + UUID.randomUUID().toString();
        PaymentRequest paymentRequest = new PaymentRequest(request.userId(), idempotencyKey);

        PaymentResponse payment = remotePaymentEnabled
                ? requestRemotePayment(order.orderId(), paymentRequest)
                : processPaymentUseCase.execute(order.orderId(), paymentRequest);
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

    private PaymentResponse requestRemotePayment(Long orderId, PaymentRequest request) {
        if (remotePaymentBaseUrl == null || remotePaymentBaseUrl.isBlank()) {
            throw new IllegalStateException("demo.payment.remote.base-url must be set when demo.payment.remote.enabled=true");
        }

        RestClient client = restClientBuilder.baseUrl(remotePaymentBaseUrl).build();
        return client.post()
                .uri("/api/orders/{orderId}/payment", orderId)
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }

    @Transactional(readOnly = true)
    protected Order getUpdatedOrder(Long orderId) {
        return orderRepository.findByIdOrThrow(orderId);
    }
}
