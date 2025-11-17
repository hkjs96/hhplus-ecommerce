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
