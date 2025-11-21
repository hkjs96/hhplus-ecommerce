package io.hhplus.ecommerce.infrastructure.persistence.payment;

import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotencyRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 결제 멱등성 키 JPA Repository
 * <p>
 * UNIQUE 제약 조건(uk_idempotency_key)으로 중복 요청 차단
 * - 동일한 idempotencyKey로 동시 INSERT 시도 시 DataIntegrityViolationException 발생
 * - 애플리케이션 레벨에서 이를 감지하여 409 Conflict 반환
 */
@Repository
@Primary
public interface JpaPaymentIdempotencyRepository
    extends JpaRepository<PaymentIdempotency, Long>, PaymentIdempotencyRepository {

    @Override
    Optional<PaymentIdempotency> findByIdempotencyKey(String idempotencyKey);

    @Override
    PaymentIdempotency save(PaymentIdempotency paymentIdempotency);
}
