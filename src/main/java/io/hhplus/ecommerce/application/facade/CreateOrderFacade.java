package io.hhplus.ecommerce.application.facade;

import io.hhplus.ecommerce.application.order.dto.CreateOrderRequest;
import io.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import io.hhplus.ecommerce.application.usecase.order.CreateOrderUseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * CreateOrderFacade - 주문 생성 시 낙관적 락 예외 처리
 *
 * 개선 사항:
 * 1. 율무 코치님 피드백: "낙관적 락 예외는 트랜잭션 커밋 시점에 발생"
 * 2. @Transactional 메서드 외부에서 OptimisticLockingFailureException 처리
 * 3. 재시도 전략 (최대 3회, Exponential Backoff)
 *
 * 사용 이유:
 * - CreateOrderUseCase는 @Transactional 메서드
 * - product.decreaseStock() 호출 시 낙관적 락 충돌 가능
 * - 트랜잭션 커밋 시점 예외는 메서드 내부에서 잡을 수 없음
 * - Facade 패턴으로 외부에서 예외 처리 및 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateOrderFacade {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final CreateOrderUseCase createOrderUseCase;

    /**
     * 주문 생성 with 낙관적 락 재시도
     *
     * @param request 주문 생성 요청
     * @return 주문 생성 응답
     * @throws BusinessException 재시도 실패 시
     */
    public CreateOrderResponse createOrderWithRetry(CreateOrderRequest request) {
        int attemptCount = 0;

        while (attemptCount < MAX_RETRY_COUNT) {
            try {
                attemptCount++;
                log.debug("Order creation attempt {}/{} for userId: {}",
                    attemptCount, MAX_RETRY_COUNT, request.userId());

                // @Transactional 메서드 호출 (예외는 커밋 시점에 발생)
                return createOrderUseCase.execute(request);

            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict during order creation. " +
                         "Attempt {}/{}, userId: {}, error: {}",
                         attemptCount, MAX_RETRY_COUNT, request.userId(), e.getMessage());

                // 마지막 재시도까지 실패한 경우
                if (attemptCount >= MAX_RETRY_COUNT) {
                    log.error("Order creation failed after {} attempts. userId: {}",
                        MAX_RETRY_COUNT, request.userId());
                    throw new BusinessException(
                        ErrorCode.STOCK_UPDATE_CONFLICT,
                        String.format("동시 주문 처리로 인한 재고 충돌이 발생했습니다. 잠시 후 다시 시도해주세요. (시도 횟수: %d)",
                            MAX_RETRY_COUNT)
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
