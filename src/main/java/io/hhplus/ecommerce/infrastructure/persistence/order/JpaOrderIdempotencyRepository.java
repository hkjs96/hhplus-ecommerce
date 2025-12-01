package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 멱등성 JPA Repository
 */
public interface JpaOrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    /**
     * 멱등성 키로 조회
     */
    Optional<OrderIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 만료된 데이터 삭제 (Batch Job용)
     */
    @Modifying
    @Query("DELETE FROM OrderIdempotency o WHERE o.expiresAt < :now")
    void deleteByExpiresAtBefore(LocalDateTime now);
}
