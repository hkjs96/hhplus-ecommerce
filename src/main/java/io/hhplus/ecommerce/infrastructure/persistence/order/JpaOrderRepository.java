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

    /**
     * Fetch Join을 사용한 Order 조회 (N+1 해결)
     *
     * 장점:
     * - 한 번의 JOIN 쿼리로 Order + OrderItem + Product 모두 조회
     * - Lazy Loading으로 인한 추가 쿼리 발생 없음
     * - 명시적이고 제어 가능
     *
     * 주의사항:
     * - 페이징 사용 시 메모리에서 처리 (setFirstResult/setMaxResults 경고)
     * - 일대다 관계에서 중복 데이터 발생 가능 (DISTINCT 필수)
     * - 카테시안 곱 발생 가능 (여러 컬렉션 Fetch Join 시)
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.userId = :userId " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithItems(@Param("userId") Long userId);

    /**
     * 단일 Order Fetch Join 조회 (상세 페이지용)
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

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
