package io.hhplus.ecommerce.domain.order;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class Order {

    private String id;
    private String userId;
    private Long subtotalAmount;   // 소계 (할인 전 금액)
    private Long discountAmount;   // 할인 금액
    private Long totalAmount;      // 최종 금액 (소계 - 할인)
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public static Order create(String id, String userId, Long subtotalAmount, Long discountAmount) {
        validateAmounts(subtotalAmount, discountAmount);

        Long totalAmount = subtotalAmount - discountAmount;
        LocalDateTime now = LocalDateTime.now();

        return new Order(
            id,
            userId,
            subtotalAmount,
            discountAmount,
            totalAmount,
            OrderStatus.PENDING,
            now,
            null  // paidAt은 결제 완료 시 설정
        );
    }

    public void complete() {
        validateStatusForComplete();

        this.status = OrderStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public void cancel() {
        validateStatusForCancel();

        this.status = OrderStatus.CANCELLED;
    }

    public boolean isPending() {
        return this.status == OrderStatus.PENDING;
    }

    public boolean isCompleted() {
        return this.status == OrderStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateAmounts(Long subtotalAmount, Long discountAmount) {
        if (subtotalAmount == null || subtotalAmount <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문 금액은 0보다 커야 합니다"
            );
        }

        if (discountAmount == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인 금액은 필수입니다 (0일 수 있음)"
            );
        }

        if (discountAmount < 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인 금액은 0 이상이어야 합니다"
            );
        }

        if (discountAmount > subtotalAmount) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인 금액이 주문 금액보다 클 수 없습니다"
            );
        }
    }

    private void validateStatusForComplete() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                String.format("결제 대기 중인 주문만 완료할 수 있습니다. 현재 상태: %s", this.status)
            );
        }
    }

    private void validateStatusForCancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                String.format("결제 대기 중인 주문만 취소할 수 있습니다. 현재 상태: %s", this.status)
            );
        }
    }
}
