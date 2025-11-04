package io.hhplus.ecommerce.common.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외
 * Week 3: Domain Layer에서 발생하는 비즈니스 규칙 위반 예외
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
