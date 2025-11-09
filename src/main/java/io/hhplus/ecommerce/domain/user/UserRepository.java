package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String id);

    User save(User user);

    /**
     * ID로 User를 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id User ID
     * @return User 엔티티
     * @throws BusinessException 사용자를 찾을 수 없을 때
     */
    default User findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.USER_NOT_FOUND,
                "사용자를 찾을 수 없습니다. userId: " + id
            ));
    }
}
