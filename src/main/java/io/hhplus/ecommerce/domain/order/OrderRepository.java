package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findById(Long id);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findAll();

    List<Order> findByUserId(Long userId);

    Order save(Order order);

    /**
     * ID로 Order를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id Order ID (BIGINT)
     * @return Order 엔티티
     * @throws BusinessException 주문을 찾을 수 없을 때
     */
    default Order findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_NOT_FOUND,
                "주문을 찾을 수 없습니다. orderId: " + id
            ));
    }

    /**
     * 주문 번호(Business ID)로 Order를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param orderNumber Order Number (e.g., "ORD-20250111-001")
     * @return Order 엔티티
     * @throws BusinessException 주문을 찾을 수 없을 때
     */
    default Order findByOrderNumberOrThrow(String orderNumber) {
        return findByOrderNumber(orderNumber)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_NOT_FOUND,
                "주문을 찾을 수 없습니다. orderNumber: " + orderNumber
            ));
    }
}
