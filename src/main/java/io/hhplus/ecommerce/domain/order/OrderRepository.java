package io.hhplus.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface OrderRepository {

    /**
     * 주문 ID로 조회
     */
    Optional<Order> findById(String id);

    /**
     * 사용자 ID로 주문 목록 조회
     */
    List<Order> findByUserId(String userId);

    /**
     * 사용자 ID와 상태로 주문 목록 조회
     */
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);

    /**
     * 모든 주문 조회
     */
    List<Order> findAll();

    /**
     * 주문 저장 (생성 및 업데이트)
     */
    Order save(Order order);

    /**
     * 주문 삭제
     */
    void deleteById(String id);

    /**
     * 주문 존재 여부 확인
     */
    boolean existsById(String id);
}
