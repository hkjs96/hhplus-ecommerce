package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InMemory Order Repository (Legacy)
 *
 * Week 3에서 사용하던 InMemory 구현체입니다.
 * Week 4부터는 JpaOrderRepository를 사용하므로, @Profile("inmemory")로 분리되었습니다.
 */
@Repository
@Profile("inmemory")
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> storage = new ConcurrentHashMap<>();
    private final Map<String, Order> orderNumberIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return Optional.ofNullable(orderNumberIndex.get(orderNumber));
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return storage.values().stream()
                .filter(order -> userId.equals(order.getUserId()))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))  // 최신순
                .toList();
    }

    @Override
    public Order save(Order order) {
        // ID가 없으면 새로 생성 (신규 저장)
        if (order.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            // Reflection으로 ID 설정 (JPA Entity는 protected setter가 없음)
            try {
                var idField = Order.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(order, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(order.getId(), order);
        orderNumberIndex.put(order.getOrderNumber(), order);
        return order;
    }

    public void clear() {
        storage.clear();
        orderNumberIndex.clear();
        idGenerator.set(1);
    }
}
