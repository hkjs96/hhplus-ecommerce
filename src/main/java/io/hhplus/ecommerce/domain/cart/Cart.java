package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class Cart {

    private String id;
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Cart create(String id, String userId) {
        validateUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        return new Cart(id, userId, now, now);
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "사용자 ID는 필수입니다"
            );
        }
    }
}
