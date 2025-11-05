package io.hhplus.ecommerce.presentation.common;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("Business exception occurred: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                e.getErrorCode().getCode(),
                e.getMessage()
        );

        HttpStatus status = mapErrorCodeToHttpStatus(e.getErrorCode());
        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected exception occurred", e);

        ErrorResponse errorResponse = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case PRODUCT_NOT_FOUND, ORDER_NOT_FOUND, USER_NOT_FOUND, CART_NOT_FOUND, CART_ITEM_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case INSUFFICIENT_STOCK, COUPON_SOLD_OUT, INSUFFICIENT_BALANCE ->
                    HttpStatus.CONFLICT;
            case INVALID_QUANTITY, INVALID_ORDER_STATUS, INVALID_COUPON, EXPIRED_COUPON,
                 ALREADY_ISSUED_COUPON, INVALID_CHARGE_AMOUNT, INVALID_INPUT ->
                    HttpStatus.BAD_REQUEST;
            case PAYMENT_FAILED ->
                    HttpStatus.PAYMENT_REQUIRED;
            default ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
