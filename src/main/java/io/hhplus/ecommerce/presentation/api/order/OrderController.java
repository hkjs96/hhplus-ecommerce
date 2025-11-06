package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.application.order.OrderService;
import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.OrderListResponse;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<OrderListResponse> getOrders(
            @NotBlank(message = "사용자 ID는 필수입니다") @RequestParam String userId,
            @RequestParam(required = false) String status
    ) {
        OrderListResponse response = orderService.getOrders(userId, status);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @NotBlank(message = "주문 ID는 필수입니다") @PathVariable String orderId,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse response = orderService.processPayment(orderId, request);
        return ResponseEntity.ok(response);
    }
}
