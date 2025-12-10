package io.hhplus.ecommerce.infrastructure.persistence.payment;

import io.hhplus.ecommerce.domain.payment.PaymentIdempotency;
import io.hhplus.ecommerce.domain.payment.PaymentIdempotencyRepository;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 결제 멱등성 키 JPA Repository
 * <p>
 * UNIQUE 제약 조건(uk_idempotency_key)으로 중복 요청 차단
 * - 동일한 idempotencyKey로 동시 INSERT 시도 시 DataIntegrityViolationException 발생
 * - 애플리케이션 레벨에서 이를 감지하여 409 Conflict 반환
 * <p>
 * Pessimistic Lock (SELECT FOR UPDATE)로 동시성 제어 강화
 * - findByIdempotencyKeyWithLock(): SELECT FOR UPDATE로 동시 요청 차단
 * - 첫 번째 요청이 트랜잭션을 완료할 때까지 다른 요청은 대기
 */
@Repository
@Primary
public interface JpaPaymentIdempotencyRepository
    extends JpaRepository<PaymentIdempotency, Long>, PaymentIdempotencyRepository {

    @Override
    Optional<PaymentIdempotency> findByIdempotencyKey(String idempotencyKey);

    /**
     * 멱등성 키 조회 with Pessimistic Lock (SELECT FOR UPDATE)
     * <p>
     * 동시 요청 시 첫 번째 요청이 완료될 때까지 대기
     * - 첫 번째: 데이터 없음 → NULL 반환 → 새로 생성
     * - 두 번째: 첫 번째 완료 대기 → PROCESSING 조회 → 409 Conflict
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentIdempotency p WHERE p.idempotencyKey = :idempotencyKey")
    Optional<PaymentIdempotency> findByIdempotencyKeyWithLock(@Param("idempotencyKey") String idempotencyKey);

    @Override
    @SuppressWarnings("unchecked")
    PaymentIdempotency save(PaymentIdempotency paymentIdempotency);

    /**
     * 멱등성 키 개수 조회 (테스트용)
     * UNIQUE 제약조건으로 인해 최대 1개만 존재
     */
    @Override
    @Query("SELECT COUNT(p) FROM PaymentIdempotency p WHERE p.idempotencyKey = :idempotencyKey")
    long countByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    // count()는 JpaRepository에서 상속받음
}

