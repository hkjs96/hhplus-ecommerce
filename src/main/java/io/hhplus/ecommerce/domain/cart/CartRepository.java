package io.hhplus.ecommerce.domain.cart;

import java.util.List;
import java.util.Optional;

public interface CartRepository {

    Optional<Cart> findByUserId(Long userId);

    Cart save(Cart cart);

    /**
     * 사용자 장바구니 + 아이템 + 상품 조회 (Native Query)
     * STEP 08 성능 최적화 - N+1 문제 해결
     */
    List<CartWithItemsProjection> findCartWithItemsByUserId(Long userId);
}
