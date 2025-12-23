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
     * 멱등성 키로 조회 (Pessimistic Lock)
     * - 동시성 제어: 동일 키를 여러 트랜잭션이 동시에 수정하는 것을 방지
     */
    Optional<OrderIdempotency> findByIdempotencyKeyWithLock(String idempotencyKey);

    /**
     * 저장
     */
    OrderIdempotency save(OrderIdempotency orderIdempotency);

    /**
     * 만료된 멱등성 키 삭제 (Batch Job용)
     */
    void deleteExpired();
}
