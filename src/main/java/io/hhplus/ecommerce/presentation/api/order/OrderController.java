package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.application.facade.CreateOrderFacade;
import io.hhplus.ecommerce.application.facade.OrderFacade;
import io.hhplus.ecommerce.application.facade.OrderPaymentFacade;
import io.hhplus.ecommerce.application.order.dto.*;
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

    // 조회 플로우: UseCase 직접 주입
    private final GetOrdersUseCase getOrdersUseCase;

    // 동시성 제어가 필요한 플로우: Facade 사용 (낙관적 락 재시도)
    private final CreateOrderFacade createOrderFacade;  // 주문 생성 (재고 차감)
    private final OrderPaymentFacade orderPaymentFacade;  // 결제 처리

    // 복잡한 플로우: Facade 사용
    private final OrderFacade orderFacade;  // 주문 생성 + 결제 한번에

    @GetMapping
    public ResponseEntity<OrderListResponse> getOrders(
            @RequestParam Long userId,
            @RequestParam(required = false) String status
    ) {
        OrderListResponse response = getOrdersUseCase.execute(userId, status);
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 생성 API
     *
     * 개선: CreateOrderFacade 사용 (낙관적 락 재시도)
     * - 이유: product.decreaseStock() 호출 시 낙관적 락 충돌 가능
     * - 율무 코치님 피드백: 트랜잭션 커밋 시점 예외는 외부에서 처리
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = createOrderFacade.createOrderWithRetry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
