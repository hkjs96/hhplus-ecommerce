package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.application.user.dto.UserResponse;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserUseCase {

    private final UserRepository userRepository;

    public UserResponse execute(Long userId) {
        log.info("Getting user info for userId: {}", userId);

        User user = userRepository.findByIdOrThrow(userId);
        return UserResponse.from(user);
    }
}
