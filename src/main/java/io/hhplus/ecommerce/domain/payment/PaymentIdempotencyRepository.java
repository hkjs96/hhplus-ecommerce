package io.hhplus.ecommerce.domain.payment;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.Optional;

/**
 * 결제 멱등성 키 Repository
 * <p>
 * 중복 결제 방지를 위한 멱등성 키 관리
 */
public interface PaymentIdempotencyRepository {

    /**
     * 멱등성 키로 조회
     */
    Optional<PaymentIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키 저장
     * UNIQUE 제약 조건으로 중복 요청 시 DataIntegrityViolationException 발생
     */
    PaymentIdempotency save(PaymentIdempotency paymentIdempotency);

    /**
     * 멱등성 키로 조회 (없으면 예외)
     */
    default PaymentIdempotency findByIdempotencyKeyOrThrow(String idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "멱등성 키를 찾을 수 없습니다. idempotencyKey: " + idempotencyKey
            ));
    }

    /**
     * 멱등성 키 개수 조회 (테스트용)
     */
    long countByIdempotencyKey(String idempotencyKey);

    /**
     * 전체 멱등성 키 개수 조회 (테스트용)
     */
    long count();
}
