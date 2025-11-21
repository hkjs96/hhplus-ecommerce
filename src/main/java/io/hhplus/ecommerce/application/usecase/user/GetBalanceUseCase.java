package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.application.user.dto.BalanceResponse;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetBalanceUseCase {

    private final UserRepository userRepository;

    public BalanceResponse execute(Long userId) {
        log.info("Getting balance for userId: {}", userId);

        User user = userRepository.findByIdOrThrow(userId);
        return BalanceResponse.of(user.getId(), user.getBalance());
    }
}
