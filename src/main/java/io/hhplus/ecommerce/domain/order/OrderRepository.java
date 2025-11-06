package io.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findById(String id);

    List<Order> findAll();

    List<Order> findByUserId(String userId);

    Order save(Order order);
}
