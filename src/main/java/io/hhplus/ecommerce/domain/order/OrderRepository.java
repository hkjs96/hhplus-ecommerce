package io.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findById(String id);

    List<Order> findAll();

    Order save(Order order);
}
