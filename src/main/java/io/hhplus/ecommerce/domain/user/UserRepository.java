package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    User save(User user);

    default User findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.USER_NOT_FOUND,
                "사용자를 찾을 수 없습니다. userId: " + id
            ));
    }

    default User findByEmailOrThrow(String email) {
        return findByEmail(email)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.USER_NOT_FOUND,
                "사용자를 찾을 수 없습니다. email: " + email
            ));
    }
}
