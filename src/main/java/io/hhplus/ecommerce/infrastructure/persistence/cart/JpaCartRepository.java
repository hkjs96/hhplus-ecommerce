package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
public interface JpaCartRepository extends JpaRepository<Cart, Long>, CartRepository {

    @Override
    Optional<Cart> findByUserId(Long userId);

    @Override
    Cart save(Cart cart);
}
