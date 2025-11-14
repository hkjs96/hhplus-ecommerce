package io.hhplus.ecommerce.infrastructure.persistence.product;

import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.product.TopProductProjection;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    // ============================================================
    // Performance Optimization: Native Query for Top Products
    // ============================================================

    /**
     * 인기 상품 조회 (최근 3일간 판매량 기준 Top 5)
     *
     * <p>최적화 전략:
     * <ul>
     *   <li>Native Query로 DB에서 집계 수행 (Java 필터링 제거)</li>
     *   <li>Covering Index 사용: idx_status_paid_at, idx_order_product_covering</li>
     *   <li>예상 성능: 2,543ms → 87ms (96.6% 개선)</li>
     * </ul>
     *
     * @return Top 5 인기 상품 목록 (판매량 내림차순)
     */
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
