package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.common.BaseEntity;
import io.hhplus.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@NoArgsConstructor
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;  // FK to carts (Cart 엔티티 미구현으로 ID만 사용)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_product"))
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    public static CartItem create(Long cartId, Product product, Integer quantity) {
        validateCartId(cartId);
        validateProduct(product);
        validateQuantity(quantity);

        CartItem cartItem = new CartItem();
        cartItem.cartId = cartId;
        cartItem.product = product;
        cartItem.quantity = quantity;
        // createdAt은 JPA Auditing이 자동 처리

        return cartItem;
    }

    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

    public java.time.LocalDateTime getAddedAt() {
        return getCreatedAt();
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

    private static void validateProduct(Product product) {
        if (product == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품은 필수입니다"
            );
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
