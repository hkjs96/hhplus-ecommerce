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
     * 멱등성 키 조회 또는 생성 (트랜잭션 + Pessimistic Lock)
     * <p>
     * Pessimistic Lock (SELECT FOR UPDATE)로 동시성 제어
     * - 첫 번째 요청: Lock 획득 → 데이터 없음 → 생성 → 커밋 → Lock 해제
     * - 두 번째 요청: Lock 대기 → 첫 번째 완료 후 Lock 획득 → PROCESSING 조회 → 409 Conflict
     */
    @Transactional
    public PaymentIdempotencyResult getOrCreate(PaymentRequest request) {
        Optional<PaymentIdempotency> existing = paymentIdempotencyRepository
            .findByIdempotencyKeyWithLock(request.idempotencyKey());

        if (existing.isPresent()) {
            PaymentIdempotency idempotency = existing.get();

            // COMPLETED: 기존 결과 반환
            if (idempotency.isCompleted()) {
                log.info("Found completed payment for idempotencyKey: {}", request.idempotencyKey());
                PaymentResponse cachedResponse = deserializeResponse(idempotency.getResponsePayload());
                return PaymentIdempotencyResult.completed(cachedResponse);
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
            return PaymentIdempotencyResult.retry(idempotency);
        }

        // 새로 생성 (PROCESSING 상태)
        try {
            PaymentIdempotency newKey = PaymentIdempotency.create(request.idempotencyKey(), request.userId());
            PaymentIdempotency saved = paymentIdempotencyRepository.save(newKey);
            return PaymentIdempotencyResult.newRequest(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate idempotency key creation attempted: {}", request.idempotencyKey());
            throw new BusinessException(
                ErrorCode.DUPLICATE_REQUEST,
                "동일한 결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
            );
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
