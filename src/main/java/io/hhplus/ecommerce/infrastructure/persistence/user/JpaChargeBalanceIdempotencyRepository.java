package io.hhplus.ecommerce.infrastructure.persistence.user;

import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for ChargeBalanceIdempotency
 */
public interface JpaChargeBalanceIdempotencyRepository extends JpaRepository<ChargeBalanceIdempotency, Long> {

    /**
     * 멱등성 키로 조회
     */
    Optional<ChargeBalanceIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키 존재 여부 확인
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}
