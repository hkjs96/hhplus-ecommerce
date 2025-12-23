package io.hhplus.ecommerce.domain.cart;

import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByUserId(Long userId);

    Optional<Cart> findByUserIdForUpdate(Long userId);

    Cart save(Cart cart);
}
