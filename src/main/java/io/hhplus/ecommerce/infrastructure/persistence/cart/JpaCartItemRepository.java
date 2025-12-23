package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public interface JpaCartItemRepository extends JpaRepository<CartItem, Long>, CartItemRepository {

    // Explicitly declare methods to resolve ambiguity with CartItemRepository
    @Override
    Optional<CartItem> findById(Long id);

    @Override
    CartItem save(CartItem cartItem);

    @Override
    List<CartItem> findAll();

    @Override
    void deleteById(Long id);

    @Override
    boolean existsById(Long id);

    @Override
    List<CartItem> findByCartId(Long cartId);

    @Override
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Override
    void deleteByCartId(Long cartId);
}
