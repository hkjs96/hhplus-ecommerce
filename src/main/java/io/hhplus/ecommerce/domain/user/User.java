package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_email", columnList = "email")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private Long balance;  // 포인트 잔액 (내부 포인트 시스템, PG 없음)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static User create(String email, String username) {
        validateEmail(email);
        validateUsername(username);

        User user = new User();
        user.email = email;
        user.username = username;
        user.balance = 0L;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();

        return user;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void charge(Long amount) {
        validateChargeAmount(amount);

        this.balance += amount;
        // updatedAt은 JPA @PreUpdate에서 자동 처리
    }

    public void deduct(Long amount) {
        validateDeductAmount(amount);
        validateSufficientBalance(amount);

        this.balance -= amount;
        // updatedAt은 JPA @PreUpdate에서 자동 처리
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
