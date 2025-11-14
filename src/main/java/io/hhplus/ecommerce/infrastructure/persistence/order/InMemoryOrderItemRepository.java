package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("inmemory")
public class InMemoryOrderItemRepository implements OrderItemRepository {

    private final Map<Long, OrderItem> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
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
        // ID가 없으면 새로 생성 (신규 저장)
        if (orderItem.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            // Reflection으로 ID 설정 (JPA Entity는 protected setter가 없음)
            try {
                var idField = OrderItem.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(orderItem, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    public void clear() {
        storage.clear();
        idGenerator.set(1);
    }
}
