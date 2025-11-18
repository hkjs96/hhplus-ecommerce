package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.OrderItemResponse;
import io.hhplus.ecommerce.application.order.dto.OrderListResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.persistence.order.JpaOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrdersUseCase {

    private final JpaOrderRepository orderRepository;  // Fetch Join 메서드 사용을 위해 JpaOrderRepository 직접 주입
    private final UserRepository userRepository;

    public OrderListResponse execute(Long userId, String status) {
        log.info("Getting orders for user: {} with status: {} using Fetch Join", userId, status);

        // 1. 사용자 존재 확인
        userRepository.findByIdOrThrow(userId);

        // 2. Fetch Join으로 Order + OrderItem + Product 한 번에 조회
        //    한 번의 JOIN 쿼리로 모든 데이터 로딩, N+1 문제 완전 해결
        List<Order> orders = orderRepository.findByUserIdWithItems(userId);

        // 3. 상태 필터링 (메모리에서)
        if (status != null && !status.isEmpty()) {
            try {
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
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

        if (orders.isEmpty()) {
            log.debug("No orders found for user: {} with status: {}", userId, status);
            return OrderListResponse.of(List.of());
        }

        // 4. Order Entity → DTO 변환
        List<CreateOrderResponse> responses = orders.stream()
            .map(order -> {
                // OrderItem Entity → OrderItemResponse DTO 변환
                List<OrderItemResponse> items = order.getOrderItems().stream()
                    .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProduct().getName(),  // Fetch Join으로 이미 로딩됨 (추가 쿼리 X)
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                    ))
                    .toList();

                return new CreateOrderResponse(
                    order.getId(),
                    order.getUserId(),
                    order.getOrderNumber(),
                    items,
                    order.getSubtotalAmount(),
                    order.getDiscountAmount(),
                    order.getTotalAmount(),
                    order.getStatus().name(),
                    order.getCreatedAt()
                );
            })
            .toList();

        log.info("Found {} orders for user: {} using Fetch Join (single query)", responses.size(), userId);
        return OrderListResponse.of(responses);
    }
}
