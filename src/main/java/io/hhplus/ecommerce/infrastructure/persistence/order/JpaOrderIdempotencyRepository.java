package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 멱등성 JPA Repository
 */
public interface JpaOrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

    /**
     * 멱등성 키로 조회 (일반 조회)
     */
    Optional<OrderIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키로 조회 (Pessimistic Lock)
     * - 동시성 제어: 동일 키를 여러 트랜잭션이 동시에 수정하는 것을 방지
     * - REQUIRES_NEW 트랜잭션에서도 안전하게 동작
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oi FROM OrderIdempotency oi WHERE oi.idempotencyKey = :idempotencyKey")
    Optional<OrderIdempotency> findByIdempotencyKeyWithLock(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 만료된 데이터 삭제 (Batch Job용)
     */
    @Modifying
    @Query("DELETE FROM OrderIdempotency o WHERE o.expiresAt < :now")
    void deleteByExpiresAtBefore(LocalDateTime now);
}
