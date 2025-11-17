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

@Repository
public interface JpaProductSalesAggregateRepository extends JpaRepository<ProductSalesAggregate, Long> {

    Optional<ProductSalesAggregate> findByProductIdAndAggregationDate(Long productId, LocalDate aggregationDate);

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

    void deleteByAggregationDate(LocalDate aggregationDate);
}
