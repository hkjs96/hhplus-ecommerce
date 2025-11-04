package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 장바구니 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 CartRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryCartRepository implements CartRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, Cart> storage = new ConcurrentHashMap<>();
    // 사용자별 장바구니 인덱스 (userId -> cartId)
    private final Map<String, String> userCartIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Cart> findByUserId(String userId) {
        String cartId = userCartIndex.get(userId);
        if (cartId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(cartId));
    }

    @Override
    public List<Cart> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Cart save(Cart cart) {
        storage.put(cart.getId(), cart);
        userCartIndex.put(cart.getUserId(), cart.getId());
        return cart;
    }

    @Override
    public void deleteById(String id) {
        Cart cart = storage.remove(id);
        if (cart != null) {
            userCartIndex.remove(cart.getUserId());
        }
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }

    @Override
    public boolean existsByUserId(String userId) {
        return userCartIndex.containsKey(userId);
    }
}
