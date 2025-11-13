package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.OrderItemResponse;
import io.hhplus.ecommerce.application.order.dto.OrderListResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrdersUseCase {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderListResponse execute(Long userId, String status) {
        log.debug("Getting orders for user: {}, status: {}", userId, status);

        // 1. 사용자 존재 확인
        userRepository.findByIdOrThrow(userId);

        // 2. userId로 주문 조회
        List<Order> orders = orderRepository.findByUserId(userId);

        // 3. 상태 필터링 (optional)
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

        // 4. Order -> CreateOrderResponse 변환
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

        log.debug("Found {} orders for user: {}", responses.size(), userId);

        return OrderListResponse.of(responses);
    }
}
