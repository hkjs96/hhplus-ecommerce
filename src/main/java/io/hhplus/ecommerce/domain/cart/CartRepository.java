package io.hhplus.ecommerce.domain.cart;

import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByUserId(String userId);

    Cart save(Cart cart);
}
