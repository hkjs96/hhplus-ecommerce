package io.hhplus.ecommerce.domain.cart;

import java.time.LocalDateTime;

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
