package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.order.dto.OrderItemResponse;
import io.hhplus.ecommerce.application.order.dto.OrderListResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderWithItemsProjection;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetOrdersUseCase {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * 사용자 주문 목록 조회 (주문 상세 포함)
     * STEP 08 최적화:
     * - 기존: findByUserId() + N번 findByOrderId() + M번 findById() (N+1+M 문제)
     * - 개선: Native Query로 orders + order_items + products JOIN 조회 (1 query)
     * - 성능 향상: 예상 90%+ (N+1+M queries → 1 query)
     */
    public OrderListResponse execute(Long userId, String status) {
        log.info("Getting orders for user: {} with status: {} using optimized Native Query", userId, status);

        // 1. 사용자 존재 확인
        userRepository.findByIdOrThrow(userId);

        // 2. 상태 검증 (optional)
        String statusParam = null;
        if (status != null && !status.isEmpty()) {
            try {
                io.hhplus.ecommerce.domain.order.OrderStatus.valueOf(status.toUpperCase());
                statusParam = status.toUpperCase();
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "유효하지 않은 주문 상태입니다. status: " + status
                );
            }
        }

        // 3. Native Query로 주문 + 주문 상세 조회 (Single Query)
        List<OrderWithItemsProjection> projectionsFromDb =
            orderRepository.findOrdersWithItemsByUserId(userId, statusParam);

        if (projectionsFromDb.isEmpty()) {
            log.debug("No orders found for user: {} with status: {}", userId, statusParam);
            return OrderListResponse.of(List.of());
        }

        // 4. Flat Projection 결과를 Order 단위로 그룹핑
        Map<Long, List<OrderWithItemsProjection>> groupedByOrder = projectionsFromDb.stream()
            .collect(Collectors.groupingBy(OrderWithItemsProjection::getOrderId));

        // 5. 각 주문별로 CreateOrderResponse 생성
        List<CreateOrderResponse> responses = groupedByOrder.entrySet().stream()
            .map(entry -> {
                // 첫 번째 Projection에서 주문 정보 추출 (모든 row의 주문 정보는 동일)
                OrderWithItemsProjection firstProj = entry.getValue().get(0);

                // OrderItem 리스트 생성
                List<OrderItemResponse> items = entry.getValue().stream()
                    .map(proj -> new OrderItemResponse(
                        proj.getProductId(),
                        proj.getProductName(),
                        proj.getQuantity(),
                        proj.getUnitPrice(),
                        proj.getSubtotal()
                    ))
                    .toList();

                // CreateOrderResponse 생성
                return new CreateOrderResponse(
                    firstProj.getOrderId(),
                    firstProj.getUserId(),
                    firstProj.getOrderNumber(),
                    items,
                    firstProj.getSubtotalAmount(),
                    firstProj.getDiscountAmount(),
                    firstProj.getTotalAmount(),
                    firstProj.getStatus(),
                    firstProj.getCreatedAt()
                );
            })
            .sorted(Comparator.comparing(CreateOrderResponse::createdAt).reversed())  // 최신순 정렬
            .toList();

        log.info("Found {} orders for user: {} using optimized query", responses.size(), userId);
        return OrderListResponse.of(responses);
    }
}
