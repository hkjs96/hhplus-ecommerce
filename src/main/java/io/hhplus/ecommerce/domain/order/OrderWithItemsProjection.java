package io.hhplus.ecommerce.domain.order;

import java.time.LocalDateTime;

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
