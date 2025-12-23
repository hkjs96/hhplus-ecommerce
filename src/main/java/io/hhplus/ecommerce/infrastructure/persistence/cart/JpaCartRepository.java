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

    /**
     * 장바구니 조회 (장바구니 + 아이템 + 상품 정보 포함)
     *
     * <p>최적화 전략:
     * <ul>
     *   <li>N+1 문제 해결: 단일 JOIN 쿼리로 모든 데이터 조회</li>
     *   <li>인덱스 사용: idx_carts_user_id, idx_cart_items_cart_id, idx_cart_items_product_id</li>
     *   <li>예상 성능: 800ms → 80ms (90% 개선)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @return 장바구니 + 아이템 + 상품 정보 목록
     */
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
