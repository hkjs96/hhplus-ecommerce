package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.ProductSalesAggregate;
import io.hhplus.ecommerce.domain.product.TopProductProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ProductSalesAggregate Repository
 *
 * 상품 판매 집계 데이터 저장소
 */
@Repository
public interface JpaProductSalesAggregateRepository extends JpaRepository<ProductSalesAggregate, Long> {

    /**
     * 특정 상품의 특정 날짜 집계 데이터 조회
     */
    Optional<ProductSalesAggregate> findByProductIdAndAggregationDate(Long productId, LocalDate aggregationDate);

    /**
     * 기간별 인기 상품 Top 5 조회 (최적화된 쿼리)
     *
     * 개선 포인트:
     * 1. 사전 집계된 데이터 사용 (order_items 전체 스캔 불필요)
     * 2. aggregation_date 동등 조건 활용 (인덱스 Range Scan)
     * 3. sales_count는 이미 계산된 컬럼이므로 ORDER BY 시 인덱스 활용 가능
     * 4. LIMIT으로 필요한 데이터만 조회
     */
    @Query(value = """
        SELECT
            product_id AS productId,
            product_name AS productName,
            SUM(sales_count) AS salesCount,
            SUM(revenue) AS revenue
        FROM product_sales_aggregates
        WHERE aggregation_date >= :startDate
          AND aggregation_date <= :endDate
        GROUP BY product_id, product_name
        ORDER BY salesCount DESC
        LIMIT 5
        """, nativeQuery = true)
    List<TopProductProjection> findTopProductsByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 날짜의 집계 데이터 삭제 (재집계 전)
     */
    void deleteByAggregationDate(LocalDate aggregationDate);
}
