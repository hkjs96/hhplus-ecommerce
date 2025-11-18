package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.ProductSalesAggregate;
import io.hhplus.ecommerce.domain.product.ProductSalesAggregateRepository;
import io.hhplus.ecommerce.domain.product.TopProductProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JpaProductSalesAggregateRepository extends JpaRepository<ProductSalesAggregate, Long>, ProductSalesAggregateRepository {

    Optional<ProductSalesAggregate> findByProductIdAndAggregationDate(Long productId, LocalDate aggregationDate);

    /**
     * 기간별 인기 상품 조회 (ROLLUP 전략)
     *
     * 개선 포인트:
     * 1. 범위 조건 (>=, <=) 사용: 인덱스 활용 가능
     * 2. GROUP BY 필요: 일별 데이터를 상품별로 합산
     * 3. ORDER BY salesCount: 계산 컬럼이지만 결과셋이 작아서 허용 가능
     *
     * 인덱스: idx_date_sales (aggregation_date, sales_count DESC)
     * - WHERE 절에서 aggregation_date 범위 검색
     * - 결과가 적으므로 (최대 상품수 * 일수) filesort 부담 적음
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
     * 특정 날짜의 인기 상품 TOP 5 (동등 조건, 인덱스 최적화)
     *
     * 개선 포인트:
     * 1. aggregation_date = :date (동등 조건) → 인덱스 100% 활용
     * 2. idx_date_sales 인덱스의 sales_count DESC 활용 → 정렬 불필요
     * 3. GROUP BY 없음 → 빠른 조회
     *
     * 사용 케이스: 일일 인기 상품 (오늘의 베스트 상품)
     */
    @Query(value = """
        SELECT
            product_id AS productId,
            product_name AS productName,
            sales_count AS salesCount,
            revenue AS revenue
        FROM product_sales_aggregates
        WHERE aggregation_date = :date
        ORDER BY sales_count DESC
        LIMIT 5
        """, nativeQuery = true)
    List<TopProductProjection> findTopProductsByDate(@Param("date") LocalDate date);

    /**
     * 최근 N일 인기 상품 (IN 조건으로 동등 조건 효과)
     *
     * 개선 포인트:
     * 1. aggregation_date IN (...) → 여러 동등 조건의 집합
     * 2. 인덱스 range scan 대신 multiple equality 사용
     * 3. 데이터 양이 적으면 (3일 * 상품수) GROUP BY 부담 적음
     *
     * 사용 케이스: 최근 3일간 인기 상품
     */
    @Query(value = """
        SELECT
            product_id AS productId,
            product_name AS productName,
            SUM(sales_count) AS salesCount,
            SUM(revenue) AS revenue
        FROM product_sales_aggregates
        WHERE aggregation_date IN :dates
        GROUP BY product_id, product_name
        ORDER BY salesCount DESC
        LIMIT 5
        """, nativeQuery = true)
    List<TopProductProjection> findTopProductsByDates(@Param("dates") List<LocalDate> dates);

    void deleteByAggregationDate(LocalDate aggregationDate);
}
