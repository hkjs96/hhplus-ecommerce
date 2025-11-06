package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderItem {

    private String id;
    private String orderId;
    private String productId;
    private Integer quantity;     // 주문 수량
    private Long unitPrice;       // 주문 시점 단가 (스냅샷)
    private Long subtotal;        // 소계 (unitPrice * quantity)

    public static OrderItem create(String id, String orderId, String productId, Integer quantity, Long unitPrice) {
        validateQuantity(quantity);
        validateUnitPrice(unitPrice);

        Long subtotal = calculateSubtotal(unitPrice, quantity);

        return new OrderItem(id, orderId, productId, quantity, unitPrice, subtotal);
    }

    private static Long calculateSubtotal(Long unitPrice, Integer quantity) {
        return unitPrice * quantity;
    }

    // ====================================
    // Validation Methods
    // ====================================

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
