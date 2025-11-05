package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.application.order.OrderService;
import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable String orderId,
            @RequestBody PaymentRequest request
    ) {
        PaymentResponse response = orderService.processPayment(orderId, request);
        return ResponseEntity.ok(response);
    }
}
