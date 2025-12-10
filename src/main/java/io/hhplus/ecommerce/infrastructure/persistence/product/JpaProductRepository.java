package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.product.TopProductProjection;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public interface JpaProductRepository extends JpaRepository<Product, Long>, ProductRepository {

    // Explicitly declare methods to resolve ambiguity with ProductRepository
    @Override
    Optional<Product> findById(Long id);

    @Override
    @SuppressWarnings("unchecked")
    Product save(Product product);

    @Override
    List<Product> findAll();

    @Override
    Optional<Product> findByProductCode(String productCode);

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
     * - 재고 차감 (주문 생성 시)
     * - 충돌이 빈번한 Hot Spot 데이터
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    /**
     * Pessimistic Write Lock with NOWAIT (즉시 실패)
     * <p>
     * timeout = 0: 락 획득 즉시 실패
     * - 대기 없이 바로 예외 발생
     * - 빠른 실패가 필요한 경우 사용
     * <p>
     * 사용 시나리오:
     * - 품절 상품 즉시 안내
     * - 사용자에게 빠른 피드백 제공
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")  // NOWAIT
    })
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLockNoWait(@Param("id") Long id);

    /**
     * Pessimistic Write Lock with SKIP LOCKED (건너뛰기)
     * <p>
     * timeout = -2: 락이 걸린 행은 건너뛰기
     * - MySQL 8.0+, PostgreSQL 9.5+ 지원
     * - 순서가 중요하지 않은 큐 처리에 유용
     * <p>
     * 사용 시나리오:
     * - 재고 일괄 처리 (순서 무관)
     * - 배치 작업
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")  // SKIP LOCKED
    })
    @Query("SELECT p FROM Product p WHERE p.stock > 0")
    List<Product> findAvailableProductsWithLockSkipLocked();

    // ============================================================
    // ⚠️ DEPRECATED: 실시간 집계 쿼리 (성능 이슈)
    // ============================================================
    // 문제점:
    // 1. DATE_SUB(NOW(), INTERVAL 3 DAY) → 함수 사용으로 인덱스 미활용
    // 2. GROUP BY로 매번 실시간 집계 → 데이터 증가 시 성능 저하
    // 3. ORDER BY salesCount → 계산 컬럼이므로 인덱스 사용 불가
    //
    // 해결책:
    // ProductSalesAggregate ROLLUP 테이블 사용 (JpaProductSalesAggregateRepository)
    // - 사전 집계된 데이터 조회 → 빠른 응답
    // - 인덱스 활용 가능한 쿼리 → idx_date_sales
    // - 원본 테이블 부하 없음
    //
    // 대체 메서드:
    // - JpaProductSalesAggregateRepository.findTopProductsByDate()
    // - JpaProductSalesAggregateRepository.findTopProductsByDates()
    // - JpaProductSalesAggregateRepository.findTopProductsByDateRange()
    // ============================================================

    @Deprecated
    @Query(value = """
        SELECT
            oi.product_id AS productId,
            p.name AS productName,
            COUNT(*) AS salesCount,
            SUM(oi.subtotal) AS revenue
        FROM order_items oi
        JOIN orders o ON oi.order_id = o.id
        JOIN products p ON oi.product_id = p.id
        WHERE o.status = 'COMPLETED'
          AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
        GROUP BY oi.product_id, p.name
        ORDER BY salesCount DESC
        LIMIT 5
        """, nativeQuery = true)
    List<TopProductProjection> findTopProductsByPeriod();
}

