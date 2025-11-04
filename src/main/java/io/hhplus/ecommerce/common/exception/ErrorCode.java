package io.hhplus.ecommerce.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 비즈니스 에러 코드 정의
 * Week 3: 애플리케이션 레벨 예외 처리
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ====================================
    // 상품 관련 (P)
    // ====================================
    PRODUCT_NOT_FOUND("P001", "상품을 찾을 수 없습니다"),
    INSUFFICIENT_STOCK("P002", "재고가 부족합니다"),

    // ====================================
    // 주문 관련 (O)
    // ====================================
    INVALID_QUANTITY("O001", "수량은 1 이상이어야 합니다"),
    ORDER_NOT_FOUND("O002", "주문을 찾을 수 없습니다"),
    INVALID_ORDER_STATUS("O003", "주문 상태가 올바르지 않습니다"),

    // ====================================
    // 결제 관련 (PAY)
    // ====================================
    INSUFFICIENT_BALANCE("PAY001", "잔액이 부족합니다"),
    PAYMENT_FAILED("PAY002", "결제 처리에 실패했습니다"),

    // ====================================
    // 쿠폰 관련 (C)
    // ====================================
    COUPON_SOLD_OUT("C001", "쿠폰이 모두 소진되었습니다"),
    INVALID_COUPON("C002", "유효하지 않은 쿠폰입니다"),
    EXPIRED_COUPON("C003", "만료된 쿠폰입니다"),
    ALREADY_ISSUED_COUPON("C004", "이미 발급받은 쿠폰입니다"),

    // ====================================
    // 사용자 관련 (U)
    // ====================================
    USER_NOT_FOUND("U001", "사용자를 찾을 수 없습니다"),
    INVALID_CHARGE_AMOUNT("U002", "충전 금액이 올바르지 않습니다"),

    // ====================================
    // 장바구니 관련 (CART)
    // ====================================
    CART_NOT_FOUND("CART001", "장바구니를 찾을 수 없습니다"),
    CART_ITEM_NOT_FOUND("CART002", "장바구니 상품을 찾을 수 없습니다"),

    // ====================================
    // 공통 (COMMON)
    // ====================================
    INTERNAL_SERVER_ERROR("COMMON001", "서버 내부 오류가 발생했습니다"),
    INVALID_INPUT("COMMON002", "입력값이 올바르지 않습니다");

    private final String code;
    private final String message;
}
