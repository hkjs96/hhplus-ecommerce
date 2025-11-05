package io.hhplus.ecommerce.application.user;

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

/**
 * User Application Service
 * - 사용자 조회, 포인트 충전 유스케이스 구현
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 조회
     * API: GET /users/{userId}
     */
    public UserResponse getUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + userId
                ));

        return UserResponse.from(user);
    }

    /**
     * 포인트 충전
     * API: POST /users/{userId}/balance/charge
     *
     * @param userId 사용자 ID
     * @param request 충전 요청 (amount)
     * @return 충전 후 잔액 정보
     */
    public ChargeBalanceResponse chargeBalance(String userId, ChargeBalanceRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + userId
                ));

        // 2. 포인트 충전 (Entity 메서드 호출 - Rich Domain Model)
        user.charge(request.getAmount());

        // 3. 저장
        userRepository.save(user);

        // 4. 응답 반환
        return ChargeBalanceResponse.of(
                user.getId(),
                user.getBalance(),
                request.getAmount(),
                LocalDateTime.now()
        );
    }
}
