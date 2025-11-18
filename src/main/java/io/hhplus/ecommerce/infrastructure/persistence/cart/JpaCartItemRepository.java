package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
        select ci from CartItem ci
        left join fetch ci.product p
        where ci.cartId = :cartId
        order by ci.createdAt desc
        """)
    List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);

    @Override
    @Query("SELECT ci FROM CartItem ci WHERE ci.cartId = :cartId AND ci.product.id = :productId")
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Override
    void deleteByCartId(Long cartId);
}
