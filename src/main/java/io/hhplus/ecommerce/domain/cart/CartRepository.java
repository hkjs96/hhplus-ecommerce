package io.hhplus.ecommerce.domain.cart;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface CartRepository {

    /**
     * 장바구니 ID로 조회
     */
    Optional<Cart> findById(String id);

    /**
     * 사용자 ID로 장바구니 조회
     */
    Optional<Cart> findByUserId(String userId);

    /**
     * 모든 장바구니 조회
     */
    List<Cart> findAll();

    /**
     * 장바구니 저장 (생성 및 업데이트)
     */
    Cart save(Cart cart);

    /**
     * 장바구니 삭제
     */
    void deleteById(String id);

    /**
     * 장바구니 존재 여부 확인
     */
    boolean existsById(String id);

    /**
     * 사용자의 장바구니 존재 여부 확인
     */
    boolean existsByUserId(String userId);
}
