package io.hhplus.ecommerce.application.usecase.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Optimistic Lock 재시도 서비스
 * <p>
 * 제이 코치 피드백 반영:
 * "낙관적 락 충돌 시 자동 재시도 로직을 추가하면 더 안정적입니다.
 * 재시도 횟수와 Backoff 전략을 설정하세요."
 * <p>
 * 재시도 전략:
 * - Exponential Backoff: 50ms → 100ms → 200ms → 400ms ...
 * - 최대 재시도 횟수: 10회 (기본값)
 * - 재시도 실패 시: BusinessException 발생
 * <p>
 * 사용 예시:
 * <pre>
 * {@code
 * ChargeBalanceResponse response = retryService.executeWithRetry(() -> {
 *     User user = userRepository.findById(userId).orElseThrow();
 *     user.charge(amount);
 *     userRepository.save(user);
 *     return ChargeBalanceResponse.of(...);
 * }, 10);
 * }
 * </pre>
 */
@Slf4j
@Service
public class OptimisticLockRetryService {

    private static final int DEFAULT_MAX_RETRY = 10;
    private static final long INITIAL_BACKOFF_MS = 50;

    /**
     * Optimistic Lock 재시도 실행 (기본 10회)
     *
     * @param operation 재시도할 작업
     * @param <T>       반환 타입
     * @return 작업 결과
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        return executeWithRetry(operation, DEFAULT_MAX_RETRY);
    }

    /**
     * Optimistic Lock 재시도 실행
     *
     * @param operation 재시도할 작업
     * @param maxRetry  최대 재시도 횟수
     * @param <T>       반환 타입
     * @return 작업 결과
     */
    public <T> T executeWithRetry(Supplier<T> operation, int maxRetry) {
        int retryCount = 0;

        while (retryCount < maxRetry) {
            try {
                return operation.get();

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;

                if (retryCount >= maxRetry) {
                    log.error("Optimistic Lock 최대 재시도 횟수 초과: {}/{}", retryCount, maxRetry, e);
                    throw new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "동시 요청으로 인해 처리에 실패했습니다. 잠시 후 다시 시도해주세요."
                    );
                }

                // Exponential Backoff: 50ms → 100ms → 200ms → 400ms ...
                long delayMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, retryCount - 1);
                log.warn("Optimistic Lock 충돌 발생. 재시도 {}/{} ({}ms 대기)", retryCount, maxRetry, delayMs);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("재시도 대기 중 인터럽트 발생", ie);
                    throw new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "처리 중 오류가 발생했습니다."
                    );
                }
            }
        }

        // 이 코드에는 도달하지 않음 (위에서 예외 발생)
        throw new BusinessException(
            ErrorCode.INTERNAL_SERVER_ERROR,
            "재시도 처리 중 예상치 못한 오류가 발생했습니다."
        );
    }

    /**
     * Void 작업 재시도 실행 (기본 10회)
     *
     * @param operation 재시도할 작업
     */
    public void executeWithRetryVoid(Runnable operation) {
        executeWithRetryVoid(operation, DEFAULT_MAX_RETRY);
    }

    /**
     * Void 작업 재시도 실행
     *
     * @param operation 재시도할 작업
     * @param maxRetry  최대 재시도 횟수
     */
    public void executeWithRetryVoid(Runnable operation, int maxRetry) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxRetry);
    }
}
