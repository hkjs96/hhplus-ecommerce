package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderItem JPA Repository
 *
 * Spring Data JPA의 JpaRepository를 상속받아 기본 CRUD 기능을 제공하고,
 * Domain의 OrderItemRepository 인터페이스를 구현하여 비즈니스 로직에서 사용할 수 있도록 합니다.
 *
 * @Primary:
 * - OrderItemRepository 타입의 빈이 여러 개 있을 때 기본적으로 JpaOrderItemRepository를 주입
 */
@Repository
@Primary
public interface JpaOrderItemRepository extends JpaRepository<OrderItem, Long>, OrderItemRepository {

    /**
     * 주문 ID로 주문 상품 목록 조회
     *
     * Query Method 네이밍 규칙:
     * - findByOrderId → WHERE order_id = ?
     *
     * @param orderId 주문 ID
     * @return List<OrderItem>
     */
    @Override
    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

    // JpaRepository에서 이미 제공하는 메서드들:
    // - findAll() : List<OrderItem>
    // - save(OrderItem orderItem) : OrderItem
    // - delete(OrderItem orderItem) : void
    // - existsById(Long id) : boolean
    // - count() : long
}
