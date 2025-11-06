package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    private String username;
    private Long balance;  // 포인트 잔액 (내부 포인트 시스템, PG 없음)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static User create(String id, String email, String username) {
        validateEmail(email);
        validateUsername(username);

        LocalDateTime now = LocalDateTime.now();
        return new User(id, email, username, 0L, now, now);
    }

    public void charge(Long amount) {
        validateChargeAmount(amount);

        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void deduct(Long amount) {
        validateDeductAmount(amount);
        validateSufficientBalance(amount);

        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasEnoughBalance(Long amount) {
        return this.balance >= amount;
    }

    private void validateChargeAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_CHARGE_AMOUNT,
                "충전 금액은 0보다 커야 합니다"
            );
        }
    }

    private void validateDeductAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "차감 금액은 0보다 커야 합니다"
            );
        }
    }

    private void validateSufficientBalance(Long amount) {
        if (this.balance < amount) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_BALANCE,
                String.format("잔액 부족: 현재 잔액 %d원, 요청 금액 %d원", this.balance, amount)
            );
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "이메일은 필수입니다"
            );
        }
        // 간단한 이메일 형식 검증
        if (!email.contains("@")) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "올바른 이메일 형식이 아닙니다"
            );
        }
    }

    private static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자명은 필수입니다"
            );
        }
    }
}
