package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderItemRepository implements OrderItemRepository {

    private final Map<String, OrderItem> storage = new ConcurrentHashMap<>();

    @Override
    public List<OrderItem> findByOrderId(String orderId) {
        return storage.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()))
            .toList();
    }

    @Override
    public List<OrderItem> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public OrderItem save(OrderItem orderItem) {
        storage.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    public void clear() {
        storage.clear();
    }
}
