package io.hhplus.ecommerce.application.usecase.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.order.dto.PaymentRequest;
import io.hhplus.ecommerce.application.order.dto.PaymentResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 멱등성 키 관리 서비스 (트랜잭션 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyService {

    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final ObjectMapper objectMapper;

    /**
     * 멱등성 키 조회 또는 생성 (트랜잭션)
     * <p>
     * INSERT-first 전략으로 deadlock 방지:
     * - 먼저 INSERT 시도 (unique constraint로 중복 방지)
     * - DataIntegrityViolationException 발생 시 SELECT FOR UPDATE로 조회
     * - SELECT → INSERT 패턴은 gap lock으로 인한 deadlock 유발
     * - INSERT → SELECT 패턴은 deadlock 위험이 훨씬 낮음
     */
    @Transactional
    public PaymentIdempotencyResult getOrCreate(PaymentRequest request) {
        // 먼저 INSERT 시도 (optimistic approach)
        try {
            PaymentIdempotency newKey = PaymentIdempotency.create(request.idempotencyKey(), request.userId());
            PaymentIdempotency saved = paymentIdempotencyRepository.save(newKey);
            log.debug("Created new payment idempotency: {}", request.idempotencyKey());
            return PaymentIdempotencyResult.newRequest(saved);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint 위반: 이미 존재함
            log.debug("Idempotency key already exists, fetching: {}", request.idempotencyKey());

            // 이제 pessimistic lock으로 조회
            PaymentIdempotency existing = paymentIdempotencyRepository
                .findByIdempotencyKeyWithLock(request.idempotencyKey())
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "멱등성 키를 찾을 수 없습니다: " + request.idempotencyKey()
                ));

            // COMPLETED: 기존 결과 반환
            if (existing.isCompleted()) {
                log.info("Found completed payment for idempotencyKey: {}", request.idempotencyKey());
                PaymentResponse cachedResponse = deserializeResponse(existing.getResponsePayload());
                return PaymentIdempotencyResult.completed(cachedResponse);
            }

            // PROCESSING: 동시 요청 (409 Conflict)
            if (existing.isProcessing()) {
                log.warn("Concurrent payment request detected for idempotencyKey: {}", request.idempotencyKey());
                throw new BusinessException(
                    ErrorCode.DUPLICATE_REQUEST,
                    "동일한 결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
                );
            }

            // FAILED: 재시도 가능하므로 진행
            log.info("Retrying failed payment for idempotencyKey: {}", request.idempotencyKey());
            return PaymentIdempotencyResult.retry(existing);
        }
    }

    /**
     * 멱등성 키 완료 처리 (트랜잭션)
     */
    @Transactional
    public void saveCompletion(PaymentIdempotency idempotency, Long orderId, PaymentResponse response) {
        idempotency.complete(orderId, serializeResponse(response));
        paymentIdempotencyRepository.save(idempotency);
    }

    /**
     * 멱등성 키 실패 처리 (트랜잭션)
     */
    @Transactional
    public void saveFailure(PaymentIdempotency idempotency, String errorMessage) {
        idempotency.fail(errorMessage);
        paymentIdempotencyRepository.save(idempotency);
    }

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
     * 멱등성 키 조회 결과를 담는 클래스
     */
    public static class PaymentIdempotencyResult {
        private final boolean completed;
        private final PaymentResponse cachedResponse;
        private final PaymentIdempotency idempotency;

        private PaymentIdempotencyResult(boolean completed, PaymentResponse cachedResponse, PaymentIdempotency idempotency) {
            this.completed = completed;
            this.cachedResponse = cachedResponse;
            this.idempotency = idempotency;
        }

        public static PaymentIdempotencyResult completed(PaymentResponse cachedResponse) {
            return new PaymentIdempotencyResult(true, cachedResponse, null);
        }

        public static PaymentIdempotencyResult retry(PaymentIdempotency idempotency) {
            return new PaymentIdempotencyResult(false, null, idempotency);
        }

        public static PaymentIdempotencyResult newRequest(PaymentIdempotency idempotency) {
            return new PaymentIdempotencyResult(false, null, idempotency);
        }

        public boolean isCompleted() {
            return completed;
        }

        public PaymentResponse getCachedResponse() {
            return cachedResponse;
        }

        public PaymentIdempotency getIdempotency() {
            return idempotency;
        }
    }
}
