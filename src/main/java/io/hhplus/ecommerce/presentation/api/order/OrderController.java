package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.application.facade.OrderFacade;
import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.application.usecase.order.CreateOrderUseCase;
import io.hhplus.ecommerce.application.usecase.order.GetOrdersUseCase;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    // 단순 플로우: UseCase 직접 주입
    private final CreateOrderUseCase createOrderUseCase;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final GetOrdersUseCase getOrdersUseCase;

    // 복잡한 플로우: Facade 사용
    private final OrderFacade orderFacade;

    @GetMapping
    public ResponseEntity<OrderListResponse> getOrders(
            @RequestParam Long userId,
            @RequestParam(required = false) String status
    ) {
        OrderListResponse response = getOrdersUseCase.execute(userId, status);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = createOrderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse response = processPaymentUseCase.execute(orderId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteOrderResponse> completeOrder(@Valid @RequestBody CompleteOrderRequest request) {
        CompleteOrderResponse response = orderFacade.createAndPayOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
