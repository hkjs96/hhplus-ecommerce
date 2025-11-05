package io.hhplus.ecommerce.application.user.dto;

import io.hhplus.ecommerce.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 응답 DTO
 * - 사용자 조회 API에서 사용
 */
@Getter
@Builder
@AllArgsConstructor
public class UserResponse {
    private String userId;
    private String username;
    private String email;
    private Long balance;

    /**
     * Domain Entity → DTO 변환
     */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .balance(user.getBalance())
                .build();
    }
}
