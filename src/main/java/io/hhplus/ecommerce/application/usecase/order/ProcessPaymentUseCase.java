package io.hhplus.ecommerce.application.usecase.order;

import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.infrastructure.external.PGResponse;
import io.hhplus.ecommerce.infrastructure.external.PGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 처리 UseCase
 * <p>
 * 동시성 제어: Pessimistic Lock (SELECT FOR UPDATE)
 * - 잔액 차감: Pessimistic Lock (Lost Update 절대 불가, 돈 손실 방지)
 * - 재고 차감: Pessimistic Lock (충돌 빈번, 크리티컬)
 * - 결제는 한 번 실패하면 재시도가 불가능하므로 정확성 최우선
 * <p>
 * 멱등성 제어: Idempotency Key (중복 결제 방지)
 * - 클라이언트 제공 idempotencyKey로 중복 요청 탐지
 * - COMPLETED: 기존 결과 반환
 * - PROCESSING: 409 Conflict (동시 요청)
 * - FAILED: 재시도 가능하므로 재처리
 * <p>
 * 외부 API 트랜잭션 분리: 보상 트랜잭션 패턴 (Compensation Transaction)
 * - reservePayment(): 잔액 차감만 (트랜잭션, 50ms)
 * - execute(): 외부 PG API 호출 (트랜잭션 밖, 5초)
 * - updatePaymentSuccess(): 성공 시 상태 업데이트 (트랜잭션, 50ms)
 * - compensatePayment(): 실패 시 보상 (트랜잭션, 50ms)
 * <p>
 * 제이 코치 멘토링 (docs/week5/MENTOR_QNA.md:530-667):
 * "외부 API 호출은 트랜잭션 밖으로 빼야 합니다. 레이턴시가 길어져서
 * 커넥션 풀도 고갈되고, 메모리 버퍼풀 캐시가 증가하고, Undo Log가 쌓입니다."
 * <p>
 * 보상 트랜잭션 흐름:
 * <pre>
 * 정상 흐름:
 * 잔액 차감 (✅ 완료) → PG 승인 (✅ 성공) → 주문 완료 (✅ 성공)
 *
 * 실패 시나리오 1: PG 승인 실패
 * 잔액 차감 (✅ 완료) → PG 승인 (❌ 실패)
 * → 보상: 잔액 복구 필요!
 *
 * 실패 시나리오 2: 네트워크 타임아웃
 * 잔액 차감 (✅ 완료) → PG 승인 (⏰ 타임아웃)
 * → 보상: 잔액 복구 필요!
 * </pre>
 * <p>
 * 참고: 잔액 충전은 Optimistic Lock 사용 (ChargeBalanceUseCase)
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ProcessPaymentUseCase {

    private final PaymentTransactionService transactionService;
    private final PaymentIdempotencyService idempotencyService;
    private final PGService pgService;

    /**
     * 결제 처리 (보상 트랜잭션 패턴)
     * <p>
     * 1. 멱등성 체크 (중복 결제 방지)
     * 2. reservePayment(): 잔액 차감 (트랜잭션)
     * 3. PG API 호출 (트랜잭션 밖)
     * 4. 성공 시: updatePaymentSuccess()
     * 5. 실패 시: compensatePayment()
     *
     * @param orderId 주문 ID
     * @param request 결제 요청
     * @return 결제 응답
     */
    public PaymentResponse execute(Long orderId, PaymentRequest request) {
        log.info("Processing payment for order: {}, user: {}, idempotencyKey: {}",
            orderId, request.userId(), request.idempotencyKey());

        // 0. 멱등성 키 조회 또는 생성 (트랜잭션)
        PaymentIdempotencyService.PaymentIdempotencyResult idempotencyResult = idempotencyService.getOrCreate(request);

        // COMPLETED: 기존 결과 반환 (캐시된 응답)
        if (idempotencyResult.isCompleted()) {
            log.info("Returning cached payment result for idempotencyKey: {}", request.idempotencyKey());
            return idempotencyResult.getCachedResponse();
        }

        PaymentIdempotency idempotency = idempotencyResult.getIdempotency();

        boolean reserveSucceeded = false;
        try {
            // Step 1: 잔액 차감 (트랜잭션, 50ms)
            Order order = transactionService.reservePayment(orderId, request);
            reserveSucceeded = true;  // Reserve transaction committed successfully
            log.info("Payment reserved successfully. orderId: {}, amount: {}", orderId, order.getTotalAmount());

            // Step 2: 외부 PG API 호출 (트랜잭션 밖, 5초)
            log.info("Calling external PG API...");
            PGResponse pgResponse = pgService.charge(request);

            if (pgResponse.isSuccess()) {
                // Step 3: 성공 시 상태 업데이트 및 응답 생성 (트랜잭션, 50ms)
                PaymentResponse response = transactionService.updatePaymentSuccessAndCreateResponse(
                    orderId,
                    request.userId(),
                    pgResponse.getTransactionId()
                );

                // 멱등성 키 완료 처리
                idempotencyService.saveCompletion(idempotency, orderId, response);

                log.info("Payment completed successfully. orderId: {}, txId: {}",
                    orderId, pgResponse.getTransactionId());
                return response;

            } else {
                // Step 4: PG 승인 실패 시 보상 트랜잭션 (트랜잭션, 50ms)
                log.warn("PG approval failed. orderId: {}, message: {}", orderId, pgResponse.getMessage());
                transactionService.compensatePayment(orderId, request.userId());

                // 멱등성 키 실패 처리
                idempotencyService.saveFailure(idempotency, "PG 승인 실패: " + pgResponse.getMessage());

                throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "PG 승인 실패: " + pgResponse.getMessage()
                );
            }

        } catch (BusinessException e) {
            // 비즈니스 예외 발생 시 보상 트랜잭션
            log.error("Payment failed for orderId: {}, error: {}", orderId, e.getMessage());

            // reservePayment() 성공 후 실패한 경우에만 보상 필요
            // (reservePayment() 실패 시 @Transactional이 자동 롤백 처리)
            if (reserveSucceeded && !idempotency.isFailed()) {
                try {
                    transactionService.compensatePayment(orderId, request.userId());
                } catch (Exception compensateError) {
                    log.error("Compensation failed for orderId: {}. Manual intervention required!",
                        orderId, compensateError);
                }
            }

            // idempotency가 아직 PROCESSING 상태일 때만 fail() 호출
            // (PG 실패 등으로 이미 FAILED 상태일 수 있음)
            if (!idempotency.isFailed() && !idempotency.isCompleted()) {
                idempotencyService.saveFailure(idempotency, e.getMessage());
            }
            throw e;

        } catch (Exception e) {
            // 시스템 예외 발생 시 보상 트랜잭션
            log.error("Unexpected error during payment for orderId: {}", orderId, e);

            // reservePayment() 성공 후 실패한 경우에만 보상 필요
            // (reservePayment() 실패 시 @Transactional이 자동 롤백 처리)
            if (reserveSucceeded && !idempotency.isFailed()) {
                try {
                    transactionService.compensatePayment(orderId, request.userId());
                } catch (Exception compensateError) {
                    log.error("Compensation failed for orderId: {}. Manual intervention required!",
                        orderId, compensateError);
                }
            }

            // idempotency가 아직 PROCESSING 상태일 때만 fail() 호출
            if (!idempotency.isFailed() && !idempotency.isCompleted()) {
                idempotencyService.saveFailure(idempotency, "시스템 오류: " + e.getMessage());
            }
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "결제 처리 중 오류가 발생했습니다."
            );
        }
    }
}
