package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("inmemory")
public class InMemoryCartItemRepository implements CartItemRepository {

    private final Map<Long, CartItem> storage = new ConcurrentHashMap<>();
    private final Map<String, Long> cartProductIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<CartItem> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<CartItem> findByCartId(Long cartId) {
        return storage.values().stream()
            .filter(item -> cartId.equals(item.getCartId()))
            .toList();
    }

    @Override
    public Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId) {
        String key = makeKey(cartId, productId);
        Long itemId = cartProductIndex.get(key);
        if (itemId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(itemId));
    }

    @Override
    public List<CartItem> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            try {
                var idField = CartItem.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(cartItem, newId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        storage.put(cartItem.getId(), cartItem);
        String key = makeKey(cartItem.getCartId(), cartItem.getProductId());
        cartProductIndex.put(key, cartItem.getId());
        return cartItem;
    }

    @Override
    public void deleteById(Long id) {
        CartItem cartItem = storage.remove(id);
        if (cartItem != null) {
            String key = makeKey(cartItem.getCartId(), cartItem.getProductId());
            cartProductIndex.remove(key);
        }
    }

    @Override
    public void deleteByCartId(Long cartId) {
        List<CartItem> items = findByCartId(cartId);
        items.forEach(item -> deleteById(item.getId()));
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    private String makeKey(Long cartId, Long productId) {
        return cartId + ":" + productId;
    }

    public void clear() {
        storage.clear();
        cartProductIndex.clear();
        idGenerator.set(1);
    }
}
