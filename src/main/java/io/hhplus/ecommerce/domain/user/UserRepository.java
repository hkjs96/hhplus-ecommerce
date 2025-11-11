package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    User save(User user);

    /**
     * ID로 User를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id User ID (BIGINT)
     * @return User 엔티티
     * @throws BusinessException 사용자를 찾을 수 없을 때
     */
    default User findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.USER_NOT_FOUND,
                "사용자를 찾을 수 없습니다. userId: " + id
            ));
    }

    /**
     * 이메일로 User를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param email User email (unique)
     * @return User 엔티티
     * @throws BusinessException 사용자를 찾을 수 없을 때
     */
    default User findByEmailOrThrow(String email) {
        return findByEmail(email)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.USER_NOT_FOUND,
                "사용자를 찾을 수 없습니다. email: " + email
            ));
    }
}
