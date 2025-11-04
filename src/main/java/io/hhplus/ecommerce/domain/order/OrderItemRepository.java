package io.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * 주문 항목 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface OrderItemRepository {

    /**
     * 주문 항목 ID로 조회
     */
    Optional<OrderItem> findById(String id);

    /**
     * 주문 ID로 주문 항목 목록 조회
     */
    List<OrderItem> findByOrderId(String orderId);

    /**
     * 모든 주문 항목 조회
     */
    List<OrderItem> findAll();

    /**
     * 주문 항목 저장 (생성 및 업데이트)
     */
    OrderItem save(OrderItem orderItem);

    /**
     * 주문 항목 일괄 저장
     */
    List<OrderItem> saveAll(List<OrderItem> orderItems);

    /**
     * 주문 항목 삭제
     */
    void deleteById(String id);

    /**
     * 주문 항목 존재 여부 확인
     */
    boolean existsById(String id);
}
