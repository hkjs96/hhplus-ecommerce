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
import io.hhplus.ecommerce.domain.payment.PaymentCompletedEvent;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotencyRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 * 외부 API 트랜잭션 분리: Spring Events + @TransactionalEventListener
 * - 결제 트랜잭션 커밋 후 외부 API 호출 (AFTER_COMMIT)
 * - 외부 API 실패해도 결제는 완료 상태 유지
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
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse execute(Long orderId, PaymentRequest request) {
        log.debug("Processing payment for order: {}, user: {}, idempotencyKey: {}",
            orderId, request.userId(), request.idempotencyKey());

        // 0. 멱등성 체크 (중복 결제 방지)
        // 동시성 제어: Idempotency Key (DB Unique Constraint)
        // - 5명 관점: 김데이터(O:DB Unique), 박트래픽(△:Redis), 이금융(O:필수), 최아키텍트(△:Event), 정스타트업(O)
        // - 최종 선택: DB Unique Constraint + 상태 관리
        //   · COMPLETED: 기존 결과 반환 (중복 요청)
        //   · PROCESSING: 409 Conflict (동시 요청)
        //   · FAILED: 재시도 허용
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

            // FAILED: 재시도 가능하므로 기존 키 삭제 후 진행
            log.info("Retrying failed payment for idempotencyKey: {}", request.idempotencyKey());
            paymentIdempotencyRepository.save(idempotency);  // 상태는 유지하고 진행
        }

        // 멱등성 키 생성 (PROCESSING 상태)
        PaymentIdempotency idempotency = existing.orElseGet(() -> {
            try {
                PaymentIdempotency newKey = PaymentIdempotency.create(request.idempotencyKey(), request.userId());
                return paymentIdempotencyRepository.save(newKey);
            } catch (DataIntegrityViolationException e) {
                // 동시 INSERT 시도 시 Unique Constraint 위반
                log.warn("Duplicate idempotency key creation attempted: {}", request.idempotencyKey());
                throw new BusinessException(
                    ErrorCode.DUPLICATE_REQUEST,
                    "동일한 결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
                );
            }
        });

        try {
            // 1. 주문 조회 및 사용자 조회 (Pessimistic Lock)
            // 동시성 제어: 잔액 차감 시 Pessimistic Lock (SELECT FOR UPDATE)
            // - 5명 관점: 김데이터(O), 박트래픽(X:Optimistic), 이금융(O), 최아키텍트(X:Event), 정스타트업(O)
            // - 최종 선택: Pessimistic Lock (돈 관련은 정확성 최우선)
            //   · 차감은 Lost Update 절대 불가 (돈 손실 방지)
            //   · 충돌 빈번 (결제는 동시 요청 가능성)
            //   · 재시도 불가 (한 번 실패하면 재결제 필요)
            // - 충전과 대조: 충전은 Optimistic Lock (ChargeBalanceUseCase 참고)
            Order order = orderRepository.findByIdOrThrow(orderId);
            User user = userRepository.findByIdWithLockOrThrow(request.userId());  // Pessimistic Lock

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
            // 동시성 제어: Pessimistic Lock (SELECT FOR UPDATE)
            // - 5명 관점: 김데이터(O), 박트래픽(X:Optimistic), 이금융(O), 최아키텍트(X:Event), 정스타트업(O)
            // - 최종 선택: Pessimistic Lock (충돌 빈번 + 크리티컬)
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : orderItems) {
                Product product = productRepository.findByIdWithLockOrThrow(item.getProductId());  // Pessimistic Lock
                product.decreaseStock(item.getQuantity());
                productRepository.save(product);
            }

            // 6. 잔액 차감
            user.deduct(order.getTotalAmount());
            userRepository.save(user);

            // 7. 주문 완료 처리
            order.complete();
            orderRepository.save(order);

            log.info("Payment processed successfully. orderId: {}, amount: {}", orderId, order.getTotalAmount());

            // 8. 외부 데이터 전송 이벤트 발행 (트랜잭션 커밋 후 실행)
            // 동시성 제어: 외부 API는 트랜잭션 밖에서 실행 (AFTER_COMMIT)
            // - 5명 관점: 김데이터(O), 박트래픽(△:Async), 이금융(△:보상TX), 최아키텍트(O), 정스타트업(O)
            // - 최종 선택: Spring Events + @TransactionalEventListener(AFTER_COMMIT)
            //   · DB 트랜잭션 최소화 (외부 API 대기 시간 제외)
            //   · 외부 API 실패해도 결제는 완료 상태 유지
            //   · 향후 @Async + 메시지 큐로 확장 가능
            PaymentCompletedEvent event = PaymentCompletedEvent.of(
                order.getId(),
                user.getId(),
                order.getTotalAmount(),
                order.getPaidAt()
            );
            eventPublisher.publishEvent(event);
            log.debug("PaymentCompletedEvent published. orderId: {}", orderId);

            PaymentResponse response = PaymentResponse.of(
                    order.getId(),
                    order.getTotalAmount(),
                    user.getBalance(),
                    "SUCCESS",
                    "EVENT_PUBLISHED",  // 외부 API는 이벤트로 분리됨
                    order.getPaidAt()
            );

            // 9. 멱등성 키 완료 처리 (응답 저장)
            idempotency.complete(orderId, serializeResponse(response));
            paymentIdempotencyRepository.save(idempotency);

            return response;

        } catch (BusinessException e) {
            // 비즈니스 예외 발생 시 FAILED 상태로 변경
            log.error("Payment failed for orderId: {}, idempotencyKey: {}, error: {}",
                orderId, request.idempotencyKey(), e.getMessage());
            idempotency.fail(e.getMessage());
            paymentIdempotencyRepository.save(idempotency);
            throw e;

        } catch (Exception e) {
            // 시스템 예외 발생 시 FAILED 상태로 변경
            log.error("Unexpected error during payment for orderId: {}, idempotencyKey: {}",
                orderId, request.idempotencyKey(), e);
            idempotency.fail("시스템 오류: " + e.getMessage());
            paymentIdempotencyRepository.save(idempotency);
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "결제 처리 중 오류가 발생했습니다."
            );
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
}
