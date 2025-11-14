package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    // Note: findById, save, findAll are provided by JpaRepository
    // Only declare domain-specific methods here

    Optional<Order> findById(Long id);  // Declared for InMemoryOrderRepository compatibility

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findAll();  // Declared for InMemoryOrderRepository compatibility

    List<Order> findByUserId(Long userId);

    Order save(Order order);  // Declared for InMemoryOrderRepository compatibility

    /**
     * 주문 내역 조회 (주문 + 주문 상세 + 상품 정보 포함)
     * STEP 08 성능 최적화 Native Query - N+1 문제 해결
     */
    List<OrderWithItemsProjection> findOrdersWithItemsByUserId(Long userId, String status);

    default Order findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_NOT_FOUND,
                "주문을 찾을 수 없습니다. orderId: " + id
            ));
    }

    default Order findByOrderNumberOrThrow(String orderNumber) {
        return findByOrderNumber(orderNumber)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_NOT_FOUND,
                "주문을 찾을 수 없습니다. orderNumber: " + orderNumber
            ));
    }
}
