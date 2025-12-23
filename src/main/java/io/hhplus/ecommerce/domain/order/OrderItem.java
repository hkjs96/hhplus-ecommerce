package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_product_id", columnList = "product_id")
    }
)
@Getter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_product"))
    private Product product;

    @Column(nullable = false)
    private Integer quantity;     // 주문 수량

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;       // 주문 시점 단가 (스냅샷)

    @Column(nullable = false)
    private Long subtotal;        // 소계 (unitPrice * quantity)

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

    public void setOrder(Order order) {
        this.order = order;
        if (order != null && !order.getOrderItems().contains(this)) {
            order.getOrderItems().add(this);
        }
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

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
