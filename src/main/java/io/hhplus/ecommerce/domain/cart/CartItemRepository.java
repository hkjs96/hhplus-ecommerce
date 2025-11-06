package io.hhplus.ecommerce.domain.cart;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    Optional<CartItem> findById(String id);

    List<CartItem> findByCartId(String cartId);

    Optional<CartItem> findByCartIdAndProductId(String cartId, String productId);

    List<CartItem> findAll();

    CartItem save(CartItem cartItem);

    void deleteById(String id);

    void deleteByCartId(String cartId);

    boolean existsById(String id);
}
