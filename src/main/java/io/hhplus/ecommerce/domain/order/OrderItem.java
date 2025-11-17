package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OrderItem Entity
 *
 * JPA Entity 생성자가 protected인 이유:
 * 1. JPA 스펙 요구사항: 리플렉션을 통한 인스턴스 생성을 위해 기본 생성자 필요
 * 2. 도메인 무결성 보호: public 생성자 노출 방지로 정적 팩토리 메서드(create)를 통한 생성 강제
 * 3. 프록시 생성 지원: Hibernate가 지연 로딩 프록시 객체 생성 시 사용
 */
@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_product_id", columnList = "product_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * OrderItem-Order 다대일 연관관계 (연관관계 주인)
     * - fetch: LAZY로 설정하여 필요할 때만 조회
     * - optional: false로 설정하여 Order는 필수
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    /**
     * OrderItem-Product 다대일 연관관계
     * - fetch: LAZY로 설정하여 필요할 때만 조회
     * - optional: false로 설정하여 Product는 필수
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_product"))
    private Product product;

    @Column(nullable = false)
    private Integer quantity;     // 주문 수량

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;       // 주문 시점 단가 (스냅샷)

    @Column(nullable = false)
    private Long subtotal;        // 소계 (unitPrice * quantity)

    /**
     * OrderItem 생성 (Entity 기반 - 권장)
     * 연관관계를 명시적으로 설정
     */
    public static OrderItem create(Order order, Product product, Integer quantity, Long unitPrice) {
        validateOrder(order);
        validateProduct(product);
        validateQuantity(quantity);
        validateUnitPrice(unitPrice);

        Long subtotal = calculateSubtotal(unitPrice, quantity);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.quantity = quantity;
        orderItem.unitPrice = unitPrice;
        orderItem.subtotal = subtotal;

        return orderItem;
    }

    /**
     * 연관관계 편의 메서드: Order 설정
     */
    public void setOrder(Order order) {
        this.order = order;
        if (order != null && !order.getOrderItems().contains(this)) {
            order.getOrderItems().add(this);
        }
    }

    /**
     * 연관관계 편의 메서드: Product 설정
     */
    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * Product ID 조회 (하위 호환성)
     */
    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

    /**
     * Order ID 조회 (하위 호환성)
     */
    public Long getOrderId() {
        return order != null ? order.getId() : null;
    }

    private static Long calculateSubtotal(Long unitPrice, Integer quantity) {
        return unitPrice * quantity;
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateOrder(Order order) {
        if (order == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문은 필수입니다"
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

    private static void validateUnitPrice(Long unitPrice) {
        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품 가격은 0보다 커야 합니다"
            );
        }
    }
}
