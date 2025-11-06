package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public List<Order> findByUserId(String userId) {
        return storage.values().stream()
                .filter(order -> userId.equals(order.getUserId()))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))  // 최신순
                .toList();
    }

    @Override
    public Order save(Order order) {
        storage.put(order.getId(), order);
        return order;
    }

    public void clear() {
        storage.clear();
    }
}
