package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 장바구니 엔티티 (Rich Domain Model)
 * Week 3: Pure Java Entity (JPA 어노테이션 없음)
 *
 * 비즈니스 규칙:
 * - 사용자당 하나의 장바구니
 * - 장바구니는 사용자가 로그인하면 자동 생성
 */
@Getter
@AllArgsConstructor
public class Cart {

    private String id;
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 장바구니 생성 (Factory Method)
     */
    public static Cart create(String id, String userId) {
        validateUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        return new Cart(id, userId, now, now);
    }

    /**
     * 장바구니 업데이트 (항목 추가/수정/삭제 시 호출)
     */
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }
}
