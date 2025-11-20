package io.hhplus.ecommerce.application.usecase.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotencyRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.external.PGResponse;
import io.hhplus.ecommerce.infrastructure.external.PGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final PGService pgService;
    private final ObjectMapper objectMapper;

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

        // 0. 멱등성 체크 (중복 결제 방지)
        Optional<PaymentIdempotency> existing = paymentIdempotencyRepository
            .findByIdempotencyKey(request.idempotencyKey());

        if (existing.isPresent()) {
            PaymentIdempotency idempotency = existing.get();

            // COMPLETED: 기존 결과 반환
            if (idempotency.isCompleted()) {
                log.info("Returning cached payment result for idempotencyKey: {}", request.idempotencyKey());
                return deserializeResponse(idempotency.getResponsePayload());
            }

            // PROCESSING: 동시 요청 (409 Conflict)
            if (idempotency.isProcessing()) {
                log.warn("Concurrent payment request detected for idempotencyKey: {}", request.idempotencyKey());
                throw new BusinessException(
                    ErrorCode.DUPLICATE_REQUEST,
                    "동일한 결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
                );
            }

            // FAILED: 재시도 가능하므로 진행
            log.info("Retrying failed payment for idempotencyKey: {}", request.idempotencyKey());
        }

        // 멱등성 키 생성 (PROCESSING 상태)
        PaymentIdempotency idempotency = existing.orElseGet(() -> {
            try {
                PaymentIdempotency newKey = PaymentIdempotency.create(request.idempotencyKey(), request.userId());
                return paymentIdempotencyRepository.save(newKey);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate idempotency key creation attempted: {}", request.idempotencyKey());
                throw new BusinessException(
                    ErrorCode.DUPLICATE_REQUEST,
                    "동일한 결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
                );
            }
        });

        boolean reserveSucceeded = false;
        try {
            // Step 1: 잔액 차감 (트랜잭션, 50ms)
            Order order = reservePayment(orderId, request);
            reserveSucceeded = true;  // Reserve transaction committed successfully
            log.info("Payment reserved successfully. orderId: {}, amount: {}", orderId, order.getTotalAmount());

            // Step 2: 외부 PG API 호출 (트랜잭션 밖, 5초)
            log.info("Calling external PG API...");
            PGResponse pgResponse = pgService.charge(request);

            if (pgResponse.isSuccess()) {
                // Step 3: 성공 시 상태 업데이트 및 응답 생성 (트랜잭션, 50ms)
                PaymentResponse response = updatePaymentSuccessAndCreateResponse(
                    orderId,
                    request.userId(),
                    pgResponse.getTransactionId()
                );

                // 멱등성 키 완료 처리
                saveIdempotencyCompletion(idempotency, orderId, response);

                log.info("Payment completed successfully. orderId: {}, txId: {}",
                    orderId, pgResponse.getTransactionId());
                return response;

            } else {
                // Step 4: PG 승인 실패 시 보상 트랜잭션 (트랜잭션, 50ms)
                log.warn("PG approval failed. orderId: {}, message: {}", orderId, pgResponse.getMessage());
                compensatePayment(orderId, request.userId());

                // 멱등성 키 실패 처리
                saveIdempotencyFailure(idempotency, "PG 승인 실패: " + pgResponse.getMessage());

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
                    compensatePayment(orderId, request.userId());
                } catch (Exception compensateError) {
                    log.error("Compensation failed for orderId: {}. Manual intervention required!",
                        orderId, compensateError);
                }
            }

            // idempotency가 아직 PROCESSING 상태일 때만 fail() 호출
            // (PG 실패 등으로 이미 FAILED 상태일 수 있음)
            if (!idempotency.isFailed() && !idempotency.isCompleted()) {
                saveIdempotencyFailure(idempotency, e.getMessage());
            }
            throw e;

        } catch (Exception e) {
            // 시스템 예외 발생 시 보상 트랜잭션
            log.error("Unexpected error during payment for orderId: {}", orderId, e);

            // reservePayment() 성공 후 실패한 경우에만 보상 필요
            // (reservePayment() 실패 시 @Transactional이 자동 롤백 처리)
            if (reserveSucceeded && !idempotency.isFailed()) {
                try {
                    compensatePayment(orderId, request.userId());
                } catch (Exception compensateError) {
                    log.error("Compensation failed for orderId: {}. Manual intervention required!",
                        orderId, compensateError);
                }
            }

            // idempotency가 아직 PROCESSING 상태일 때만 fail() 호출
            if (!idempotency.isFailed() && !idempotency.isCompleted()) {
                saveIdempotencyFailure(idempotency, "시스템 오류: " + e.getMessage());
            }
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "결제 처리 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * Step 1: 잔액 차감 (트랜잭션)
     * <p>
     * DB 트랜잭션 내에서 수행:
     * - 주문 조회 및 검증
     * - 잔액 차감 (Pessimistic Lock)
     * - 재고 차감 (Pessimistic Lock)
     * - 주문 상태 PENDING 유지 (결제 대기)
     * <p>
     * 트랜잭션 보유 시간: 약 50ms (외부 API 제외)
     *
     * @param orderId 주문 ID
     * @param request 결제 요청
     * @return 주문 엔티티
     */
    @Transactional
    protected Order reservePayment(Long orderId, PaymentRequest request) {
        log.debug("Reserving payment for order: {}", orderId);

        // 1. 주문 조회 및 사용자 조회 (Pessimistic Lock)
        Order order = orderRepository.findByIdOrThrow(orderId);
        User user = userRepository.findByIdWithLockOrThrow(request.userId());

        // 2. 주문 소유자 검증
        if (!order.getUserId().equals(user.getId())) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "주문한 사용자와 결제 요청 사용자가 다릅니다."
            );
        }

        // 3. 주문 상태 검증
        if (order.getStatus() != io.hhplus.ecommerce.domain.order.OrderStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.INVALID_ORDER_STATUS,
                "결제할 수 없는 주문 상태입니다. 현재 상태: " + order.getStatus()
            );
        }

        // 4. 잔액 검증
        if (user.getBalance() < order.getTotalAmount()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_BALANCE,
                String.format("잔액이 부족합니다. (필요: %d원, 보유: %d원)",
                    order.getTotalAmount(), user.getBalance())
            );
        }

        // 5. 재고 차감 (결제 성공 시에만 재고 감소)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByIdWithLockOrThrow(item.getProductId());
            product.decreaseStock(item.getQuantity());
            productRepository.save(product);
        }

        // 6. 잔액 차감
        user.deduct(order.getTotalAmount());
        userRepository.save(user);

        log.debug("Payment reserved. orderId: {}, amount: {}", orderId, order.getTotalAmount());
        return order;
    }

    /**
     * Step 3: 결제 성공 시 상태 업데이트 및 응답 생성 (트랜잭션)
     * <p>
     * PG 승인 성공 후 DB 상태 업데이트:
     * - 주문 상태 → COMPLETED
     * - 결제 완료 시간 기록
     * - User 잔액 조회
     * - PaymentResponse 생성
     * <p>
     * 트랜잭션 보유 시간: 약 50ms
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param pgTransactionId PG사 트랜잭션 ID
     * @return PaymentResponse
     */
    @Transactional
    protected PaymentResponse updatePaymentSuccessAndCreateResponse(
            Long orderId,
            Long userId,
            String pgTransactionId) {
        log.debug("Updating payment success. orderId: {}, txId: {}", orderId, pgTransactionId);

        // 주문 상태 업데이트
        Order order = orderRepository.findByIdOrThrow(orderId);
        order.complete();
        orderRepository.save(order);

        // 사용자 잔액 조회
        User user = userRepository.findByIdOrThrow(userId);

        log.info("Payment status updated to COMPLETED. orderId: {}, txId: {}", orderId, pgTransactionId);

        // 응답 생성
        return PaymentResponse.of(
            order.getId(),
            order.getTotalAmount(),
            user.getBalance(),  // 결제 후 잔액
            "SUCCESS",
            "PG_APPROVED: " + pgTransactionId,
            order.getPaidAt()
        );
    }

    /**
     * Step 4: 결제 실패 시 보상 트랜잭션 (트랜잭션)
     * <p>
     * 잔액 차감은 성공했지만 PG 승인 실패 시:
     * - 잔액 복구 (user.charge)
     * - 재고 복구 (product.restoreStock)
     * <p>
     * 트랜잭션 보유 시간: 약 50ms
     * <p>
     * ⚠️ 보상 실패 시 수동 개입 필요 (로그 남김)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     */
    @Transactional
    protected void compensatePayment(Long orderId, Long userId) {
        log.warn("Compensating payment. orderId: {}, userId: {}", orderId, userId);

        try {
            // 1. 주문 조회
            Order order = orderRepository.findByIdOrThrow(orderId);

            // 2. 재고 복구
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                Product product = productRepository.findByIdOrThrow(item.getProductId());
                product.increaseStock(item.getQuantity());  // restoreStock → increaseStock
                productRepository.save(product);
            }

            // 3. 잔액 복구
            User user = userRepository.findByIdOrThrow(userId);
            user.charge(order.getTotalAmount());
            userRepository.save(user);

            log.info("Payment compensation completed. orderId: {}, refundedAmount: {}",
                orderId, order.getTotalAmount());

        } catch (Exception e) {
            log.error("CRITICAL: Compensation failed for orderId: {}. Manual intervention required!",
                orderId, e);
            throw e;  // 보상 실패 시 예외 발생 (수동 처리 필요)
        }
    }

    /**
     * PaymentResponse를 JSON으로 직렬화
     */
    private String serializeResponse(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentResponse", e);
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "응답 직렬화 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * JSON을 PaymentResponse로 역직렬화
     */
    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PaymentResponse", e);
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "응답 역직렬화 중 오류가 발생했습니다."
            );
        }
    }

    /**
     * 멱등성 키 완료 처리 (트랜잭션)
     */
    @Transactional
    protected void saveIdempotencyCompletion(PaymentIdempotency idempotency, Long orderId, PaymentResponse response) {
        idempotency.complete(orderId, serializeResponse(response));
        paymentIdempotencyRepository.save(idempotency);
    }

    /**
     * 멱등성 키 실패 처리 (트랜잭션)
     */
    @Transactional
    protected void saveIdempotencyFailure(PaymentIdempotency idempotency, String errorMessage) {
        idempotency.fail(errorMessage);
        paymentIdempotencyRepository.save(idempotency);
    }
}
