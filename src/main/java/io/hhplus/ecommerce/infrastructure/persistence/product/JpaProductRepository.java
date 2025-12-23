package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.product.TopProductProjection;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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
    Product save(Product product);

    @Override
    List<Product> findAll();

    @Override
    Optional<Product> findByProductCode(String productCode);

    /**
     * Pessimistic Write Lock (SELECT FOR UPDATE)
     * - 재고 차감 시 사용
     * - 충돌이 빈번한 경우 Optimistic Lock보다 효율적
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

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
