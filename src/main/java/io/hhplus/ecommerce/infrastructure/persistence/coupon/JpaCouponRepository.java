package io.hhplus.ecommerce.infrastructure.persistence.coupon;

import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
public interface JpaCouponRepository extends JpaRepository<Coupon, Long>, CouponRepository {

    // Explicitly declare methods to resolve ambiguity with CouponRepository
    @Override
    Optional<Coupon> findById(Long id);

    @Override
    Coupon save(Coupon coupon);

    @Override
    Optional<Coupon> findByCouponCode(String couponCode);

    /**
     * Pessimistic Write Lock (SELECT FOR UPDATE) with Timeout
     * <p>
     * 제이 코치 피드백 반영:
     * "락 타임아웃 설정도 확인해보면 좋겠어요. 비관적 락에서 @QueryHints로
     * jakarta.persistence.lock.timeout을 설정하지 않으면 무한 대기할 수 있거든요."
     * <p>
     * 설정:
     * - jakarta.persistence.lock.timeout = 3000ms (3초)
     * - 3초 내에 락을 획득하지 못하면 PessimisticLockException 발생
     * - 무한 대기 방지
     * <p>
     * 사용 시나리오:
     * - 쿠폰 발급 (선착순 이벤트)
     * - 충돌이 빈번한 Hot Spot 데이터
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);

    /**
     * Pessimistic Write Lock with NOWAIT (즉시 실패)
     * <p>
     * timeout = 0: 락 획득 즉시 실패
     * - 대기 없이 바로 예외 발생
     * - 빠른 실패가 필요한 경우 사용
     * <p>
     * 사용 시나리오:
     * - 선착순 쿠폰 품절 즉시 안내
     * - 사용자에게 빠른 피드백 제공
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")  // NOWAIT
    })
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLockNoWait(@Param("id") Long id);

    /**
     * Pessimistic Write Lock with SKIP LOCKED (건너뛰기)
     * <p>
     * timeout = -2: 락이 걸린 행은 건너뛰기
     * - MySQL 8.0+, PostgreSQL 9.5+ 지원
     * - 순서가 중요하지 않은 쿠폰 일괄 처리에 유용
     * <p>
     * 사용 시나리오:
     * - 쿠폰 일괄 처리 (순서 무관)
     * - 배치 작업
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")  // SKIP LOCKED
    })
    @Query("SELECT c FROM Coupon c WHERE c.totalQuantity > c.issuedQuantity")
    java.util.List<Coupon> findAvailableCouponsWithLockSkipLocked();
}
