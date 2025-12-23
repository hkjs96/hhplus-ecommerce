package io.hhplus.ecommerce.domain.cart;

import java.time.LocalDateTime;

/**
 * 장바구니 + 장바구니 아이템 조회 Native Query Projection
 *
 * 용도: GetCartUseCase에서 사용
 * 쿼리: Native Query로 carts + cart_items + products JOIN 결과 매핑
 */
public interface CartWithItemsProjection {

    // Cart 정보
    Long getCartId();

    Long getUserId();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    // CartItem 정보
    Long getItemId();

    Long getProductId();

    String getProductName();

    Long getPrice();

    Integer getQuantity();

    LocalDateTime getAddedAt();

    // Product 재고 정보
    Integer getStock();
}
