package io.hhplus.ecommerce.infrastructure.persistence.user;

import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotency;
import io.hhplus.ecommerce.domain.user.ChargeBalanceIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ChargeBalanceIdempotency Repository 구현체
 */
@Repository
@RequiredArgsConstructor
public class ChargeBalanceIdempotencyRepositoryImpl implements ChargeBalanceIdempotencyRepository {

    private final JpaChargeBalanceIdempotencyRepository jpaRepository;

    @Override
    public Optional<ChargeBalanceIdempotency> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public ChargeBalanceIdempotency save(ChargeBalanceIdempotency chargeBalanceIdempotency) {
        return jpaRepository.save(chargeBalanceIdempotency);
    }
}
