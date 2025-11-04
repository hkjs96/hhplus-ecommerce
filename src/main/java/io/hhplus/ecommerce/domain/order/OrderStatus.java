package io.hhplus.ecommerce.domain.order;

/**
 * 주문 상태
 */
public enum OrderStatus {
    /**
     * 대기중 (주문 생성됨, 결제 전)
     */
    PENDING,

    /**
     * 완료 (결제 완료)
     */
    COMPLETED,

    /**
     * 취소
     */
    CANCELLED
}
