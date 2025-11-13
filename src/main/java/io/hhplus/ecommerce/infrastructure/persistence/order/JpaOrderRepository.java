package io.hhplus.ecommerce.infrastructure.persistence.order;

import io.hhplus.ecommerce.domain.order.Order;
import io.hhplus.ecommerce.domain.order.OrderRepository;
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
}
