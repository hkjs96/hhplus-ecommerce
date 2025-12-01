package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderIdempotency;
import io.hhplus.ecommerce.domain.order.OrderIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 멱등성 Repository 구현체
 */
@Repository
@RequiredArgsConstructor
public class OrderIdempotencyRepositoryImpl implements OrderIdempotencyRepository {

    private final JpaOrderIdempotencyRepository jpaRepository;

    @Override
    public Optional<OrderIdempotency> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    @Transactional
    public OrderIdempotency save(OrderIdempotency orderIdempotency) {
        return jpaRepository.save(orderIdempotency);
    }

    @Override
    @Transactional
    public void deleteExpired() {
        jpaRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
