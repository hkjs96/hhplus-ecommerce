package io.hhplus.ecommerce.application.user;

import io.hhplus.ecommerce.application.user.dto.BalanceResponse;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceResponse;
import io.hhplus.ecommerce.application.user.dto.UserResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getUser(Long userId) {
        User user = userRepository.findByIdOrThrow(userId);
        return UserResponse.from(user);
    }

    public BalanceResponse getBalance(Long userId) {
        User user = userRepository.findByIdOrThrow(userId);
        return BalanceResponse.of(user.getId(), user.getBalance());
    }

    public ChargeBalanceResponse chargeBalance(Long userId, ChargeBalanceRequest request) {
        User user = userRepository.findByIdOrThrow(userId);
        user.charge(request.getAmount());
        userRepository.save(user);

        return ChargeBalanceResponse.of(
                user.getId(),
                user.getBalance(),
                request.getAmount(),
                LocalDateTime.now()
        );
    }
}
