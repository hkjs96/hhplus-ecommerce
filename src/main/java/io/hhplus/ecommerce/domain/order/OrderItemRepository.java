package io.hhplus.ecommerce.domain.order;

import java.util.List;

public interface OrderItemRepository {

    List<OrderItem> findByOrderId(String orderId);

    List<OrderItem> findAll();

    OrderItem save(OrderItem orderItem);
}
