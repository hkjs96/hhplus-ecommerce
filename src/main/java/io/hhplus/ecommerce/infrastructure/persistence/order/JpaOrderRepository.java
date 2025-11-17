package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
import io.hhplus.ecommerce.domain.order.OrderWithItemsProjection;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public interface JpaOrderRepository extends JpaRepository<Order, Long>, OrderRepository {

    // Explicitly declare methods to resolve ambiguity with OrderRepository
    @Override
    Optional<Order> findById(Long id);

    @Override
    Order save(Order order);

    @Override
    List<Order> findAll();

    @Override
    Optional<Order> findByOrderNumber(String orderNumber);

    @Override
    @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserId(@Param("userId") Long userId);

    // JpaRepository에서 이미 제공하는 메서드들:
    // - delete(Order order) : void
    // - existsById(Long id) : boolean
    // - count() : long

    // ============================================================
    // Performance Optimization: Native Query for Orders with Items
    // ============================================================

    @Query(value = """
        SELECT
            o.id AS orderId,
            o.order_number AS orderNumber,
            o.user_id AS userId,
            o.subtotal_amount AS subtotalAmount,
            o.discount_amount AS discountAmount,
            o.total_amount AS totalAmount,
            o.status AS status,
            o.created_at AS createdAt,
            oi.id AS itemId,
            oi.product_id AS productId,
            p.name AS productName,
            oi.quantity AS quantity,
            oi.unit_price AS unitPrice,
            oi.subtotal AS subtotal
        FROM orders o
        JOIN order_items oi ON o.id = oi.order_id
        JOIN products p ON oi.product_id = p.id
        WHERE o.user_id = :userId
          AND (:status IS NULL OR o.status = :status)
        ORDER BY o.created_at DESC
        """, nativeQuery = true)
    List<OrderWithItemsProjection> findOrdersWithItemsByUserId(
        @Param("userId") Long userId,
        @Param("status") String status
    );
}
