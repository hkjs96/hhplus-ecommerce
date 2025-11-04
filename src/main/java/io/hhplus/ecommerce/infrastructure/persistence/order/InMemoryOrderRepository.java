package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 주문 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 OrderRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, Order> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Order> findByUserId(String userId) {
        return storage.values().stream()
            .filter(order -> userId.equals(order.getUserId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByUserIdAndStatus(String userId, OrderStatus status) {
        return storage.values().stream()
            .filter(order -> userId.equals(order.getUserId()))
            .filter(order -> status == order.getStatus())
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Order save(Order order) {
        storage.put(order.getId(), order);
        return order;
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
