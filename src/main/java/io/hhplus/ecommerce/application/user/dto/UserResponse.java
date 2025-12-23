package io.hhplus.ecommerce.application.user.dto;

import io.hhplus.ecommerce.domain.user.User;

public record UserResponse(
    Long userId,
    String username,
    String email,
    Long balance
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBalance()
        );
    }
}
