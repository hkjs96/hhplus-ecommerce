package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.common.BaseEntity;
import io.hhplus.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CartItem Entity
 *
 * 개선 사항:
 * 1. Cart 엔티티 직접 참조로 변경 (간접 참조 → 직접 참조)
 * 2. Product와 양방향 관계 (선택적)
 * 3. Fetch Join을 통한 N+1 문제 해결 가능
 */
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

    /**
     * 개선: Cart 엔티티 직접 참조
     * - 기존: Long cartId (간접 참조)
     * - 개선: Cart cart (직접 참조)
     * - 장점: Fetch Join으로 한 번에 조회 가능 (쿼리 1번)
     */
    @Setter(AccessLevel.PROTECTED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_cart"))
    private Cart cart;

    /**
     * Product 엔티티 직접 참조 (Product : CartItem = 1 : N)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_product"))
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * CartItem 생성 (Cart 엔티티 직접 참조)
     */
    public static CartItem create(Cart cart, Product product, Integer quantity) {
        validateCart(cart);
        validateProduct(product);
        validateQuantity(quantity);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.product = product;
        cartItem.quantity = quantity;
        // createdAt은 JPA Auditing이 자동 처리

        return cartItem;
    }

    /**
     * 하위 호환성을 위한 메서드 (기존 코드 호환)
     */
    public Long getCartId() {
        return cart != null ? cart.getId() : null;
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

    /**
     * 양방향 관계 설정 메서드 (Cart.addCartItem/removeCartItem에서 호출)
     */
    protected void setCart(Cart cart) {
        this.cart = cart;
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateCart(Cart cart) {
        if (cart == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "장바구니는 필수입니다"
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
