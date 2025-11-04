package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 주문 항목 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 OrderItemRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryOrderItemRepository implements OrderItemRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, OrderItem> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderItem> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<OrderItem> findByOrderId(String orderId) {
        return storage.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public OrderItem save(OrderItem orderItem) {
        storage.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        orderItems.forEach(item -> storage.put(item.getId(), item));
        return orderItems;
    }

    @Override
    public void deleteById(String id) {
        storage.remove(id);
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}
