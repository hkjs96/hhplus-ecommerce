package io.hhplus.ecommerce.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ================================
    // 상품 관련 (P0xx)
    // ================================
    PRODUCT_NOT_FOUND(
        "P001",
        "상품을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    INSUFFICIENT_STOCK(
        "P002",
        "재고가 부족합니다",
        HttpStatus.BAD_REQUEST
    ),

    // ================================
    // 주문 관련 (O0xx)
    // ================================
    EMPTY_CART(
        "O001",
        "장바구니가 비어있습니다",
        HttpStatus.BAD_REQUEST
    ),
    ORDER_NOT_FOUND(
        "O002",
        "주문을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    INVALID_QUANTITY(
        "O003",
        "유효하지 않은 수량입니다",
        HttpStatus.BAD_REQUEST
    ),
    ORDER_ALREADY_PAID(
        "O004",
        "이미 결제된 주문입니다",
        HttpStatus.BAD_REQUEST
    ),

    // ================================
    // 결제 관련 (PAY0xx)
    // ================================
    INSUFFICIENT_BALANCE(
        "PAY001",
        "잔액이 부족합니다",
        HttpStatus.BAD_REQUEST
    ),
    PAYMENT_FAILED(
        "PAY002",
        "결제에 실패했습니다",
        HttpStatus.BAD_REQUEST
    ),
    STOCK_DEDUCTION_FAILED(
        "PAY003",
        "재고 차감에 실패했습니다. 다시 시도해주세요",
        HttpStatus.CONFLICT
    ),

    // ================================
    // 쿠폰 관련 (C0xx)
    // ================================
    COUPON_NOT_FOUND(
        "C001",
        "쿠폰을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    COUPON_SOLD_OUT(
        "C002",
        "쿠폰이 모두 소진되었습니다",
        HttpStatus.CONFLICT
    ),
    INVALID_COUPON(
        "C003",
        "유효하지 않은 쿠폰입니다",
        HttpStatus.BAD_REQUEST
    ),
    EXPIRED_COUPON(
        "C004",
        "만료된 쿠폰입니다",
        HttpStatus.BAD_REQUEST
    ),
    ALREADY_ISSUED(
        "C005",
        "이미 발급받은 쿠폰입니다",
        HttpStatus.CONFLICT
    ),
    COUPON_ISSUE_FAILED(
        "C006",
        "쿠폰 발급에 실패했습니다. 다시 시도해주세요",
        HttpStatus.CONFLICT
    ),

    // ================================
    // 사용자 관련 (U0xx)
    // ================================
    USER_NOT_FOUND(
        "U001",
        "사용자를 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),
    INVALID_AMOUNT(
        "U002",
        "유효하지 않은 금액입니다",
        HttpStatus.BAD_REQUEST
    ),

    // ================================
    // 장바구니 관련 (CART0xx)
    // ================================
    CART_ITEM_NOT_FOUND(
        "CART001",
        "장바구니 상품을 찾을 수 없습니다",
        HttpStatus.NOT_FOUND
    ),

    // ================================
    // 시스템 오류 (SYS0xx)
    // ================================
    INTERNAL_SERVER_ERROR(
        "SYS001",
        "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요",
        HttpStatus.INTERNAL_SERVER_ERROR
    ),
    EXTERNAL_API_ERROR(
        "SYS002",
        "외부 API 호출에 실패했습니다",
        HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
