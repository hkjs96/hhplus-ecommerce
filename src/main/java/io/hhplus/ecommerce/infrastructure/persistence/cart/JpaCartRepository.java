package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.cart.CartWithItemsProjection;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public interface JpaCartRepository extends JpaRepository<Cart, Long>, CartRepository {

    @Override
    Optional<Cart> findByUserId(Long userId);

    @Override
    Cart save(Cart cart);

    // ============================================================
    // Performance Optimization: Native Query for Cart with Items
    // ============================================================

    @Query(value = """
        SELECT
            c.id AS cartId,
            c.user_id AS userId,
            c.created_at AS createdAt,
            c.updated_at AS updatedAt,
            ci.id AS itemId,
            ci.product_id AS productId,
            p.name AS productName,
            p.price AS price,
            ci.quantity AS quantity,
            ci.added_at AS addedAt,
            p.stock AS stock
        FROM carts c
        LEFT JOIN cart_items ci ON c.id = ci.cart_id
        LEFT JOIN products p ON ci.product_id = p.id
        WHERE c.user_id = :userId
        ORDER BY ci.added_at DESC
        """, nativeQuery = true)
    List<CartWithItemsProjection> findCartWithItemsByUserId(@Param("userId") Long userId);
}
