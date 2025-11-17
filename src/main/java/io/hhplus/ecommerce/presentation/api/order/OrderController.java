package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.application.facade.OrderFacade;
import io.hhplus.ecommerce.application.facade.OrderPaymentFacade;
import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.application.usecase.order.CreateOrderUseCase;
import io.hhplus.ecommerce.application.usecase.order.GetOrdersUseCase;
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
    private final GetOrdersUseCase getOrdersUseCase;

    // 복잡한 플로우: Facade 사용
    private final OrderFacade orderFacade;

    // 동시성 제어가 필요한 플로우: OrderPaymentFacade 사용 (낙관적 락 재시도)
    private final OrderPaymentFacade orderPaymentFacade;

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

    /**
     * 주문 결제 처리 (낙관적 락 재시도 포함)
     *
     * OrderPaymentFacade를 사용하여:
     * - OptimisticLockingFailureException 처리
     * - 동시성 충돌 시 자동 재시도 (최대 3회)
     */
    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse response = orderPaymentFacade.processPaymentWithRetry(orderId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteOrderResponse> completeOrder(@Valid @RequestBody CompleteOrderRequest request) {
        CompleteOrderResponse response = orderFacade.createAndPayOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
