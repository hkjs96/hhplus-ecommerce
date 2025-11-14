package io.hhplus.ecommerce.domain.order;

import java.time.LocalDateTime;

/**
 * 주문 + 주문 상세 조회 Native Query Projection
 *
 * 용도: GetOrdersUseCase에서 사용
 * 쿼리: Native Query로 orders + order_items + products JOIN 결과 매핑
 */
public interface OrderWithItemsProjection {

    // Order 정보
    Long getOrderId();

    String getOrderNumber();

    Long getUserId();

    Long getSubtotalAmount();

    Long getDiscountAmount();

    Long getTotalAmount();

    String getStatus();

    LocalDateTime getCreatedAt();

    // OrderItem 정보
    Long getItemId();

    Long getProductId();

    String getProductName();

    Integer getQuantity();

    Long getUnitPrice();

    Long getSubtotal();
}
