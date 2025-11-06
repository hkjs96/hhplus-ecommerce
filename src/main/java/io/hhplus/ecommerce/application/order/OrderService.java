package io.hhplus.ecommerce.application.order;

import io.hhplus.ecommerce.application.order.dto.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + request.getUserId()
                ));

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        long subtotalAmount = 0L;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.PRODUCT_NOT_FOUND,
                            "상품을 찾을 수 없습니다. productId: " + itemReq.getProductId()
                    ));

            if (product.getStock() < itemReq.getQuantity()) {
                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        String.format("재고가 부족합니다. 상품: %s, 요청: %d, 재고: %d",
                                product.getName(), itemReq.getQuantity(), product.getStock())
                );
            }

            long itemSubtotal = product.getPrice() * itemReq.getQuantity();
            subtotalAmount += itemSubtotal;

            itemResponses.add(OrderItemResponse.of(
                    product.getId(),
                    product.getName(),
                    itemReq.getQuantity(),
                    product.getPrice(),
                    itemSubtotal
            ));
        }

        long discountAmount = 0L;
        if (request.getCouponId() != null && !request.getCouponId().isEmpty()) {
            Coupon coupon = couponRepository.findById(request.getCouponId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.INVALID_COUPON,
                            "유효하지 않은 쿠폰입니다. couponId: " + request.getCouponId()
                    ));

            coupon.validateIssuable();

            if (!userCouponRepository.existsByUserIdAndCouponId(user.getId(), coupon.getId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_COUPON,
                        "보유하지 않은 쿠폰입니다."
                );
            }

            discountAmount = (long) (subtotalAmount * coupon.getDiscountRate() / 100.0);
        }

        String orderId = "ORDER-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = Order.create(orderId, user.getId(), subtotalAmount, discountAmount);
        orderRepository.save(order);

        for (int i = 0; i < request.getItems().size(); i++) {
            OrderItemRequest itemReq = request.getItems().get(i);
            Product product = productRepository.findById(itemReq.getProductId()).orElseThrow();

            String orderItemId = orderId + "-ITEM-" + (i + 1);
            OrderItem orderItem = OrderItem.create(
                    orderItemId,
                    orderId,
                    product.getId(),
                    itemReq.getQuantity(),
                    product.getPrice()
            );
            orderItemRepository.save(orderItem);
        }

        return CreateOrderResponse.of(
                order.getId(),
                order.getUserId(),
                itemResponses,
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }

    public PaymentResponse processPayment(String orderId, PaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND,
                        "주문을 찾을 수 없습니다. orderId: " + orderId
                ));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + request.getUserId()
                ));

        if (!order.getUserId().equals(user.getId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "주문한 사용자와 결제 요청 사용자가 다릅니다."
            );
        }

        if (order.getStatus() != io.hhplus.ecommerce.domain.order.OrderStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS,
                    "결제할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus()
            );
        }

        if (user.getBalance() < order.getTotalAmount()) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE,
                    String.format("잔액이 부족합니다. (필요: %d원, 보유: %d원)",
                            order.getTotalAmount(), user.getBalance())
            );
        }

        user.deduct(order.getTotalAmount());
        userRepository.save(user);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findById(item.getProductId()).orElseThrow();
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        order.complete();
        orderRepository.save(order);

        String dataTransmission = "SUCCESS";

        return PaymentResponse.of(
                order.getId(),
                order.getTotalAmount(),
                user.getBalance(),
                "SUCCESS",
                dataTransmission,
                order.getPaidAt()
        );
    }

    public OrderListResponse getOrders(String userId, String status) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + userId
                ));

        // userId로 주문 조회
        List<Order> orders = orderRepository.findByUserId(userId);

        // 상태 필터링 (optional)
        if (status != null && !status.isEmpty()) {
            try {
                io.hhplus.ecommerce.domain.order.OrderStatus orderStatus =
                        io.hhplus.ecommerce.domain.order.OrderStatus.valueOf(status.toUpperCase());
                orders = orders.stream()
                        .filter(order -> order.getStatus() == orderStatus)
                        .toList();
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "유효하지 않은 주문 상태입니다. status: " + status
                );
            }
        }

        // Order -> CreateOrderResponse 변환
        List<CreateOrderResponse> responses = orders.stream()
                .map(order -> {
                    // OrderItem 조회
                    List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

                    List<OrderItemResponse> itemResponses = orderItems.stream()
                            .map(item -> {
                                Product product = productRepository.findById(item.getProductId())
                                        .orElse(null);
                                String productName = product != null ? product.getName() : "알 수 없음";

                                return OrderItemResponse.of(
                                        item.getProductId(),
                                        productName,
                                        item.getQuantity(),
                                        item.getUnitPrice(),
                                        item.getSubtotal()
                                );
                            })
                            .toList();

                    return CreateOrderResponse.of(
                            order.getId(),
                            order.getUserId(),
                            itemResponses,
                            order.getSubtotalAmount(),
                            order.getDiscountAmount(),
                            order.getTotalAmount(),
                            order.getStatus().name(),
                            order.getCreatedAt()
                    );
                })
                .toList();

        return OrderListResponse.of(responses);
    }
}
