package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.common.BaseEntity;
import io.hhplus.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CartItem Entity
 *
 * JPA Entity 생성자가 protected인 이유:
 * 1. JPA 스펙 요구사항: 리플렉션을 통한 인스턴스 생성을 위해 기본 생성자 필요
 * 2. 도메인 무결성 보호: public 생성자 노출 방지로 정적 팩토리 메서드(create)를 통한 생성 강제
 * 3. 프록시 생성 지원: Hibernate가 지연 로딩 프록시 객체 생성 시 사용
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;  // FK to carts (Cart 엔티티 미구현으로 ID만 사용)

    /**
     * CartItem-Product 다대일 연관관계
     * - fetch: LAZY로 설정하여 필요할 때만 조회
     * - optional: false로 설정하여 Product는 필수
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_product"))
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * CartItem 생성 (Entity 기반 - 권장)
     */
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

    /**
     * Product ID 조회 (하위 호환성)
     */
    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

    /**
     * Added At 조회 (하위 호환성 - createdAt을 addedAt으로 노출)
     */
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
