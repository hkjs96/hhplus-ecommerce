package io.hhplus.ecommerce.domain.order;

import java.util.Optional;

/**
 * 주문 멱등성 Repository 인터페이스
 */
public interface OrderIdempotencyRepository {

    /**
     * 멱등성 키로 조회
     */
    Optional<OrderIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 저장
     */
    OrderIdempotency save(OrderIdempotency orderIdempotency);

    /**
     * 만료된 멱등성 키 삭제 (Batch Job용)
     */
    void deleteExpired();
}
