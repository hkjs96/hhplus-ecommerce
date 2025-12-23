package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;  // FK to orders

    @Column(name = "product_id", nullable = false)
    private Long productId;  // FK to products

    @Column(nullable = false)
    private Integer quantity;     // 주문 수량

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;       // 주문 시점 단가 (스냅샷)

    @Column(nullable = false)
    private Long subtotal;        // 소계 (unitPrice * quantity)

    public static OrderItem create(Long orderId, Long productId, Integer quantity, Long unitPrice) {
        validateOrderId(orderId);
        validateProductId(productId);
        validateQuantity(quantity);
        validateUnitPrice(unitPrice);

        Long subtotal = calculateSubtotal(unitPrice, quantity);

        OrderItem orderItem = new OrderItem();
        orderItem.orderId = orderId;
        orderItem.productId = productId;
        orderItem.quantity = quantity;
        orderItem.unitPrice = unitPrice;
        orderItem.subtotal = subtotal;

        return orderItem;
    }

    private static Long calculateSubtotal(Long unitPrice, Integer quantity) {
        return unitPrice * quantity;
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문 ID는 필수입니다"
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

    private static void validateUnitPrice(Long unitPrice) {
        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "상품 가격은 0보다 커야 합니다"
            );
        }
    }
}
