package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.cart.CartWithItemsProjection;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("inmemory")
public class InMemoryCartRepository implements CartRepository {

    private final Map<Long, Cart> storage = new ConcurrentHashMap<>();
    private final Map<Long, Long> userCartIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        Long cartId = userCartIndex.get(userId);
        if (cartId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(cartId));
    }

    @Override
    public Cart save(Cart cart) {
        if (cart.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            try {
                var idField = Cart.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(cart, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(cart.getId(), cart);
        userCartIndex.put(cart.getUserId(), cart.getId());
        return cart;
    }

    @Override
    public List<CartWithItemsProjection> findCartWithItemsByUserId(Long userId) {
        // InMemory에서는 빈 리스트 반환 (실제 구현은 JPA에서만 사용)
        return Collections.emptyList();
    }

    public void clear() {
        storage.clear();
        userCartIndex.clear();
        idGenerator.set(1);
    }
}
