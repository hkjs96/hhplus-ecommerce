package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티 (Rich Domain Model)
 * Week 3: Pure Java Entity (JPA 어노테이션 없음)
 *
 * 비즈니스 규칙:
 * - 포인트 충전/차감 로직 포함
 * - 잔액은 0 이상이어야 함
 * - 충전 금액은 양수여야 함
 */
@Getter
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    private String username;
    private Long balance;  // 포인트 잔액 (내부 포인트 시스템, PG 없음)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 사용자 생성 (Factory Method)
     */
    public static User create(String id, String email, String username) {
        validateEmail(email);
        validateUsername(username);

        LocalDateTime now = LocalDateTime.now();
        return new User(id, email, username, 0L, now, now);
    }

    /**
     * 포인트 충전 (비즈니스 로직)
     *
     * @param amount 충전 금액
     * @throws BusinessException 충전 금액이 0 이하인 경우
     */
    public void charge(Long amount) {
        validateChargeAmount(amount);

        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 포인트 차감 (결제 시)
     *
     * @param amount 차감 금액
     * @throws BusinessException 잔액이 부족하거나 금액이 0 이하인 경우
     */
    public void deduct(Long amount) {
        validateDeductAmount(amount);
        validateSufficientBalance(amount);

        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 잔액 확인
     */
    public boolean hasEnoughBalance(Long amount) {
        return this.balance >= amount;
    }

    /**
     * 사용자 정보 업데이트
     */
    public void updateProfile(String email, String username) {
        if (email != null) {
            validateEmail(email);
            this.email = email;
        }
        if (username != null) {
            validateUsername(username);
            this.username = username;
        }
        this.updatedAt = LocalDateTime.now();
    }

    // ====================================
    // Validation Methods
    // ====================================

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
