package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final UserRepository userRepository;

    @Transactional
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        log.info("Charging balance for userId: {}, amount: {}", userId, request.amount());

        // 1. 사용자 조회
        User user = userRepository.findByIdOrThrow(userId);

        // 2. 잔액 충전
        user.charge(request.amount());
        userRepository.save(user);

        log.debug("Balance charged successfully. userId: {}, new balance: {}", userId, user.getBalance());

        // 3. 충전 결과 반환
        return ChargeBalanceResponse.of(
            user.getId(),
            user.getBalance(),
            request.amount(),
            LocalDateTime.now()
        );
    }
}
