package io.hhplus.ecommerce.application.facade;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.application.usecase.order.ProcessPaymentUseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * OrderPaymentFacade
 *
 * 낙관적 락 예외 처리 및 재시도 로직을 담당하는 Facade
 *
 * 왜 UseCase 안에서 처리하지 않나요?
 * - OptimisticLockingFailureException은 트랜잭션 커밋 시점에 발생
 * - @Transactional 메서드 내부에서는 예외를 잡을 수 없음
 * - 트랜잭션 AOP가 예외를 먼저 처리하므로 UseCase 내부 try-catch는 작동하지 않음
 *
 * Facade 패턴의 역할:
 * - @Transactional 경계 바깥에서 예외 catch
 * - 재시도 로직 구현 (최대 3회)
 * - 동시성 충돌 시 사용자에게 명확한 에러 메시지 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final ProcessPaymentUseCase processPaymentUseCase;

    /**
     * 결제 처리 with 낙관적 락 재시도
     *
     * 재시도 전략:
     * 1. OptimisticLockingFailureException 발생 시 최대 3회 재시도
     * 2. 재시도 간격: 100ms (Exponential Backoff 적용 가능)
     * 3. 최종 실패 시 사용자 친화적 에러 메시지 반환
     */
    public PaymentResponse processPaymentWithRetry(Long orderId, PaymentRequest request) {
        int attemptCount = 0;

        while (attemptCount < MAX_RETRY_COUNT) {
            try {
                attemptCount++;
                log.debug("Payment processing attempt {}/{} for orderId: {}", attemptCount, MAX_RETRY_COUNT, orderId);

                // @Transactional 메서드 호출 (예외는 커밋 시점에 발생)
                return processPaymentUseCase.execute(orderId, request);

            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on payment processing. " +
                         "Attempt {}/{}, orderId: {}, error: {}",
                         attemptCount, MAX_RETRY_COUNT, orderId, e.getMessage());

                // 마지막 재시도까지 실패한 경우
                if (attemptCount >= MAX_RETRY_COUNT) {
                    log.error("Payment processing failed after {} attempts. orderId: {}", MAX_RETRY_COUNT, orderId);
                    throw new BusinessException(
                        ErrorCode.STOCK_UPDATE_CONFLICT,
                        String.format("동시 주문 처리로 인한 충돌이 발생했습니다. 잠시 후 다시 시도해주세요. (시도 횟수: %d)", MAX_RETRY_COUNT)
                    );
                }

                // 재시도 전 대기 (Exponential Backoff)
                sleep(RETRY_DELAY_MS * attemptCount);
            }
        }

        // 도달할 수 없는 코드 (컴파일러 만족용)
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry sleep interrupted", e);
        }
    }
}
