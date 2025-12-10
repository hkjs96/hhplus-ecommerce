package io.hhplus.ecommerce.infrastructure.persistence.cart;

import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
@Primary
public interface JpaCartItemRepository extends JpaRepository<CartItem, Long>, CartItemRepository {

    // Explicitly declare methods to resolve ambiguity with CartItemRepository
    @Override
    Optional<CartItem> findById(Long id);

    @Override
    @SuppressWarnings("unchecked")
    CartItem save(CartItem cartItem);

    @Override
    List<CartItem> findAll();

    @Override
    void deleteById(Long id);

    @Override
    boolean existsById(Long id);

    @Override
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId")
    List<CartItem> findByCartId(Long cartId);

    /**
     * Fetch Join으로 CartItem + Product 한 번에 조회
     * 개선: ci.cartId → ci.cart.id (Cart 직접 참조)
     */
    @Query("""
        select ci from CartItem ci
        left join fetch ci.product p
        where ci.cart.id = :cartId
        order by ci.createdAt desc
        """)
    List<CartItem> findByCartIdWithProduct(@Param("cartId") Long cartId);

    /**
     * Fetch Join으로 CartItem + Cart + Product 모두 한 번에 조회
     * - 사용 케이스: Cart, CartItem, Product 모두 필요한 경우
     * - 쿼리 1번으로 완전한 데이터 로딩
     */
    @Query("""
        select distinct ci from CartItem ci
        left join fetch ci.cart c
        left join fetch ci.product p
        where ci.cart.id = :cartId
        order by ci.createdAt desc
        """)
    List<CartItem> findByCartIdWithCartAndProduct(@Param("cartId") Long cartId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.product.id = :productId")
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Override
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);
}

