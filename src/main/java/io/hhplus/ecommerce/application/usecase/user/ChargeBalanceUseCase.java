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

/**
 * 잔액 충전 UseCase
 * <p>
 * 동시성 제어: Optimistic Lock (@Version)
 * - 충전은 충돌 가능성이 낮고, 충돌 시 재시도 가능하므로 성능을 우선
 * - OptimisticLockingFailureException 발생 시 Controller에서 재시도 로직 필요
 * <p>
 * 참고: 잔액 차감(결제)은 Pessimistic Lock 사용 (ProcessPaymentUseCase)
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ChargeBalanceUseCase {

    private final UserRepository userRepository;

    @Transactional
    public ChargeBalanceResponse execute(Long userId, ChargeBalanceRequest request) {
        log.info("Charging balance for userId: {}, amount: {}", userId, request.amount());

        // 1. 사용자 조회 (Optimistic Lock)
        // 동시성 제어: 잔액 충전 시 Optimistic Lock (@Version)
        // - 5명 관점: 김데이터(X:Pessimistic), 박트래픽(O), 이금융(△:충전은 OK), 최아키텍트(O), 정스타트업(O)
        // - 최종 선택: Optimistic Lock
        //   · 충전은 충돌 가능성 낮음 (사용자별로 본인만 충전)
        //   · 충돌 시 재시도 가능 (금액 손실 없음)
        //   · 성능 우선 (Lock 대기 없음)
        // - 차감과 대조: 차감(결제)은 Pessimistic Lock (ProcessPaymentUseCase 참고)
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
