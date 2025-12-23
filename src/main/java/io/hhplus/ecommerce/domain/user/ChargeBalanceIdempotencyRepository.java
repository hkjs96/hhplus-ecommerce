package io.hhplus.ecommerce.domain.user;

import java.util.Optional;

/**
 * 잔액 충전 멱등성 키 Repository 인터페이스
 */
public interface ChargeBalanceIdempotencyRepository {

    /**
     * 멱등성 키로 조회
     */
    Optional<ChargeBalanceIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키 존재 여부 확인
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * 저장
     */
    ChargeBalanceIdempotency save(ChargeBalanceIdempotency chargeBalanceIdempotency);
}
