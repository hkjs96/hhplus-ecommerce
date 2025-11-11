package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_product", columnNames = {"cart_id", "product_id"})
    },
    indexes = {
        @Index(name = "idx_cart_id", columnList = "cart_id"),
        @Index(name = "idx_product_id", columnList = "product_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;  // FK to carts

    @Column(name = "product_id", nullable = false)
    private Long productId;  // FK to products

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    public static CartItem create(Long cartId, Long productId, Integer quantity) {
        validateCartId(cartId);
        validateProductId(productId);
        validateQuantity(quantity);

        CartItem cartItem = new CartItem();
        cartItem.cartId = cartId;
        cartItem.productId = productId;
        cartItem.quantity = quantity;
        cartItem.addedAt = LocalDateTime.now();

        return cartItem;
    }

    @PrePersist
    protected void onCreate() {
        if (this.addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    public void updateQuantity(Integer quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public void increaseQuantity(Integer additionalQuantity) {
        validateQuantity(additionalQuantity);
        this.quantity += additionalQuantity;
    }

    private static void validateCartId(Long cartId) {
        if (cartId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "장바구니 ID는 필수입니다"
            );
        }
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품 ID는 필수입니다"
            );
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
