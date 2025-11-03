package io.hhplus.ecommerce.presentation.api.order;

import io.hhplus.ecommerce.application.dto.order.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.presentation.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/orders")
@Tag(name = "3. 주문/결제", description = "주문 생성 및 결제 API")
public class OrderController {

    // Mock 데이터 저장소
    private final Map<String, OrderData> orderStore = new ConcurrentHashMap<>();
    private final Map<String, CartItemData> cartStore = new ConcurrentHashMap<>();
    private final Map<String, ProductInfo> productStore = new ConcurrentHashMap<>();
    private final Map<String, UserInfo> userStore = new ConcurrentHashMap<>();
    private final Map<String, CouponInfo> couponStore = new ConcurrentHashMap<>();

    public OrderController() {
        // 상품 정보
        productStore.put("P001", new ProductInfo("P001", "노트북", 890000L, 10));
        productStore.put("P002", new ProductInfo("P002", "키보드", 120000L, 50));

        // 사용자 정보
        userStore.put("U001", new UserInfo("U001", 1000000L));

        // 장바구니 (주문 생성 테스트용)
        cartStore.put("CI001", new CartItemData("CI001", "U001", "P001", 2));
        cartStore.put("CI002", new CartItemData("CI002", "U001", "P002", 1));

        // 쿠폰 정보
        couponStore.put("C001", new CouponInfo("C001", "10% 할인 쿠폰", 10));
    }

    /**
     * 3.1 주문 생성
     * POST /orders
     */
    @Operation(
        summary = "주문 생성",
        description = "장바구니의 상품을 기반으로 주문을 생성합니다. 재고와 쿠폰을 검증합니다."
    )
    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /orders - userId: {}, couponId: {}", request.getUserId(), request.getCouponId());

        // 장바구니 조회
        List<CartItemData> userCartItems = cartStore.values().stream()
            .filter(item -> item.userId.equals(request.getUserId()))
            .collect(Collectors.toList());

        if (userCartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_CART);
        }

        // 주문 항목 생성 및 재고 확인
        List<OrderItemResponse> orderItems = new ArrayList<>();
        long subtotalAmount = 0L;

        for (CartItemData cartItem : userCartItems) {
            ProductInfo product = productStore.get(cartItem.productId);
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            // 재고 확인
            if (product.stock < cartItem.quantity) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }

            long itemSubtotal = product.price * cartItem.quantity;
            OrderItemResponse orderItem = new OrderItemResponse(
                product.productId,
                product.name,
                cartItem.quantity,
                product.price,
                itemSubtotal
            );
            orderItems.add(orderItem);
            subtotalAmount += itemSubtotal;
        }

        // 쿠폰 검증 및 할인 계산
        long discountAmount = 0L;
        if (request.getCouponId() != null && !request.getCouponId().isEmpty()) {
            CouponInfo coupon = couponStore.get(request.getCouponId());
            if (coupon == null) {
                throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
            }
            discountAmount = subtotalAmount * coupon.discountRate / 100;
        }

        long totalAmount = subtotalAmount - discountAmount;

        // 주문 생성
        String orderId = "ORD-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) +
            "-" + UUID.randomUUID().toString().substring(0, 3).toUpperCase();

        OrderData order = new OrderData(
            orderId,
            request.getUserId(),
            new ArrayList<>(orderItems),
            subtotalAmount,
            discountAmount,
            totalAmount,
            "PENDING",
            request.getCouponId()
        );
        orderStore.put(orderId, order);

        OrderResponse response = new OrderResponse(
            orderId,
            orderItems,
            subtotalAmount,
            discountAmount,
            totalAmount,
            "PENDING"
        );

        return ApiResponse.success(response);
    }

    /**
     * 3.2 결제 처리
     * POST /orders/{orderId}/payment
     */
    @Operation(
        summary = "결제 처리",
        description = "주문에 대한 포인트 결제를 처리합니다. 포인트 차감 및 재고 차감이 이루어집니다."
    )
    @PostMapping("/{orderId}/payment")
    public ApiResponse<PaymentResponse> processPayment(
        @Parameter(description = "주문 ID", required = true)
        @PathVariable String orderId,
        @Valid @RequestBody PaymentRequest request
    ) {
        log.info("POST /orders/{}/payment - userId: {}", orderId, request.getUserId());

        // 주문 조회
        OrderData order = orderStore.get(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 이미 결제된 주문인지 확인
        if ("COMPLETED".equals(order.status)) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
        }

        // 사용자 정보 조회
        UserInfo user = userStore.get(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 잔액 확인
        if (user.balance < order.totalAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 포인트 차감
        user.balance -= order.totalAmount;

        // 재고 차감 (실제로는 Optimistic Lock 사용)
        for (OrderItemResponse item : order.items) {
            ProductInfo product = productStore.get(item.getProductId());
            if (product != null) {
                product.stock -= item.getQuantity();
            }
        }

        // 주문 상태 업데이트
        order.status = "COMPLETED";
        order.paidAt = LocalDateTime.now();

        // 장바구니 비우기
        cartStore.entrySet().removeIf(entry -> entry.getValue().userId.equals(request.getUserId()));

        PaymentResponse response = new PaymentResponse(
            orderId,
            order.totalAmount,
            user.balance,
            "SUCCESS",
            order.paidAt
        );

        return ApiResponse.success(response);
    }

    /**
     * 3.3 주문 조회
     * GET /orders/{orderId}
     */
    @Operation(
        summary = "주문 조회",
        description = "특정 주문의 상세 정보를 조회합니다."
    )
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(
        @Parameter(description = "주문 ID", required = true)
        @PathVariable String orderId
    ) {
        log.info("GET /orders/{}", orderId);

        OrderData order = orderStore.get(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        OrderResponse response = new OrderResponse(
            order.orderId,
            order.items,
            order.subtotalAmount,
            order.discountAmount,
            order.totalAmount,
            order.status
        );

        return ApiResponse.success(response);
    }

    // Mock 데이터 클래스
    private static class OrderData {
        String orderId;
        String userId;
        List<OrderItemResponse> items;
        Long subtotalAmount;
        Long discountAmount;
        Long totalAmount;
        String status;
        String couponId;
        LocalDateTime paidAt;

        OrderData(String orderId, String userId, List<OrderItemResponse> items,
                  Long subtotalAmount, Long discountAmount, Long totalAmount,
                  String status, String couponId) {
            this.orderId = orderId;
            this.userId = userId;
            this.items = items;
            this.subtotalAmount = subtotalAmount;
            this.discountAmount = discountAmount;
            this.totalAmount = totalAmount;
            this.status = status;
            this.couponId = couponId;
        }
    }

    private static class CartItemData {
        String cartItemId;
        String userId;
        String productId;
        Integer quantity;

        CartItemData(String cartItemId, String userId, String productId, Integer quantity) {
            this.cartItemId = cartItemId;
            this.userId = userId;
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    private static class ProductInfo {
        String productId;
        String name;
        Long price;
        Integer stock;

        ProductInfo(String productId, String name, Long price, Integer stock) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }

    private static class UserInfo {
        String userId;
        Long balance;

        UserInfo(String userId, Long balance) {
            this.userId = userId;
            this.balance = balance;
        }
    }

    private static class CouponInfo {
        String couponId;
        String name;
        Integer discountRate;

        CouponInfo(String couponId, String name, Integer discountRate) {
            this.couponId = couponId;
            this.name = name;
            this.discountRate = discountRate;
        }
    }
}
