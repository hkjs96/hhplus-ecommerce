package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCartRepository implements CartRepository {

    private final Map<String, Cart> storage = new ConcurrentHashMap<>();
    private final Map<String, String> userCartIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findByUserId(String userId) {
        String cartId = userCartIndex.get(userId);
        if (cartId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(cartId));
    }

    @Override
    public Cart save(Cart cart) {
        storage.put(cart.getId(), cart);
        userCartIndex.put(cart.getUserId(), cart.getId());
        return cart;
    }
}
