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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
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

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 멱등성 키 조회 또는 생성 (트랜잭션)
     * <p>
     * Insert-first 전략:
     * - 먼저 INSERT 시도 (UNIQUE 제약으로 중복 차단)
     * - 중복 발생 시 SELECT FOR UPDATE로 기존 키 조회 후 상태 분기
     *
     * 주의: 없는 행에 대한 FOR UPDATE는 InnoDB가 넥스트키락/갭락을 잡아
     * 데드락/대기를 늘릴 수 있으므로(코치 피드백), insert-first를 기본으로 한다.
     */
    @Transactional
    public PaymentIdempotencyResult getOrCreate(PaymentRequest request) {
        try {
            PaymentIdempotency newKey = PaymentIdempotency.create(request.idempotencyKey(), request.userId());
            PaymentIdempotency saved = paymentIdempotencyRepository.save(newKey);
            log.debug("Created new payment idempotency: {}", request.idempotencyKey());
            return PaymentIdempotencyResult.newRequest(saved);
        } catch (DataIntegrityViolationException e) {
            // 이전 INSERT 시도한 엔티티가 영속성 컨텍스트에 남아있을 수 있으므로 초기화
            entityManager.clear();
            // Unique constraint 위반: 이미 존재함 → 락 걸고 상태 확인
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
                // 응답 페이로드가 이미 채워졌다면 상태 플래그와 무관하게 캐시 응답 반환
                if (existing.getResponsePayload() != null) {
                    log.info("Returning cached response while processing for idempotencyKey: {}", request.idempotencyKey());
                    PaymentResponse cachedResponse = deserializeResponse(existing.getResponsePayload());
                    return PaymentIdempotencyResult.completed(cachedResponse);
                }

                // 짧게 재확인하여 COMPLETED/FAILED 전이 여부 확인 (동기 호출에서 캐시 반환 시도)
                for (int i = 0; i < 5; i++) {
                    Optional<PaymentIdempotency> refreshedOpt = recheckStatus(request.idempotencyKey());
                    if (refreshedOpt.isPresent()) {
                        PaymentIdempotency refreshed = refreshedOpt.get();
                        if (refreshed.isCompleted()) {
                            log.info("Found completed payment after retry for idempotencyKey: {}", request.idempotencyKey());
                            PaymentResponse cachedResponse = deserializeResponse(refreshed.getResponsePayload());
                            return PaymentIdempotencyResult.completed(cachedResponse);
                        }
                        if (refreshed.isFailed()) {
                            log.info("Retrying failed payment for idempotencyKey: {} after retry", request.idempotencyKey());
                            return PaymentIdempotencyResult.retry(refreshed);
                        }
                    }
                    try {
                        Thread.sleep(30L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

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
        entityManager.flush();  // 다음 요청에서 즉시 조회될 수 있도록 강제 반영
    }

    /**
     * 멱등성 키 실패 처리 (트랜잭션)
     */
    @Transactional
    public void saveFailure(PaymentIdempotency idempotency, String errorMessage) {
        idempotency.fail(errorMessage);
        paymentIdempotencyRepository.save(idempotency);
        entityManager.flush();
    }

    /**
     * 멱등성 키로 조회 (Phase 3용)
     */
    @Transactional(readOnly = true)
    public PaymentIdempotency findByKey(String idempotencyKey) {
        return paymentIdempotencyRepository
            .findByIdempotencyKey(idempotencyKey)
            .orElse(null);
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
     * 별도 read-only 트랜잭션으로 상태 재확인
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    protected Optional<PaymentIdempotency> recheckStatus(String idempotencyKey) {
        return paymentIdempotencyRepository.findByIdempotencyKey(idempotencyKey);
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
