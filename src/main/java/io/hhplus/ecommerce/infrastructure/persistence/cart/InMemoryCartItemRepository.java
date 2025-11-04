package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 장바구니 항목 In-Memory Repository 구현체 (Infrastructure Layer)
 * Week 3: ConcurrentHashMap 기반 Thread-safe 저장소
 *
 * DIP (Dependency Inversion Principle):
 * - Domain의 CartItemRepository 인터페이스를 구현
 * - Infrastructure가 Domain에 의존 (Domain은 Infrastructure를 모름)
 */
@Repository
public class InMemoryCartItemRepository implements CartItemRepository {

    // Thread-safe 인메모리 저장소
    private final Map<String, CartItem> storage = new ConcurrentHashMap<>();
    // 중복 체크용 인덱스 (cartId + productId)
    private final Map<String, String> cartProductIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<CartItem> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<CartItem> findByCartId(String cartId) {
        return storage.values().stream()
            .filter(item -> cartId.equals(item.getCartId()))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<CartItem> findByCartIdAndProductId(String cartId, String productId) {
        String key = makeKey(cartId, productId);
        String itemId = cartProductIndex.get(key);
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
        storage.put(cartItem.getId(), cartItem);
        // 중복 체크용 인덱스 업데이트
        String key = makeKey(cartItem.getCartId(), cartItem.getProductId());
        cartProductIndex.put(key, cartItem.getId());
        return cartItem;
    }

    @Override
    public void deleteById(String id) {
        CartItem cartItem = storage.remove(id);
        if (cartItem != null) {
            String key = makeKey(cartItem.getCartId(), cartItem.getProductId());
            cartProductIndex.remove(key);
        }
    }

    @Override
    public void deleteByCartId(String cartId) {
        List<CartItem> items = findByCartId(cartId);
        items.forEach(item -> deleteById(item.getId()));
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }

    /**
     * 복합 키 생성 (cartId + productId)
     */
    private String makeKey(String cartId, String productId) {
        return cartId + ":" + productId;
    }
}
