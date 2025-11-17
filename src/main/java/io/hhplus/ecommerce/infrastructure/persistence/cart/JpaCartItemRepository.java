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

    /**
     * Fetch Join을 사용한 CartItem 조회 (N+1 해결)
     *
     * CartItem 조회 시 Product를 함께 가져와서 N+1 문제 방지
     */
    @Query("SELECT ci FROM CartItem ci " +
           "LEFT JOIN FETCH ci.product " +
           "WHERE ci.cartId = :cartId " +
           "ORDER BY ci.createdAt DESC")
    List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);

    @Override
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Override
    void deleteByCartId(Long cartId);
}
