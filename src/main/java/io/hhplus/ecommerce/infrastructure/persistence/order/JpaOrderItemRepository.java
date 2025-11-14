package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.OrderItem;
import io.hhplus.ecommerce.domain.order.OrderItemRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
public interface JpaOrderItemRepository extends JpaRepository<OrderItem, Long>, OrderItemRepository {

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
