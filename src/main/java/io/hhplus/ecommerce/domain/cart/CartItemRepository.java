package io.hhplus.ecommerce.domain.cart;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 항목 Repository 인터페이스 (Domain Layer)
 * Week 3: 인터페이스는 Domain에, 구현체는 Infrastructure에 위치 (DIP 원칙)
 */
public interface CartItemRepository {

    /**
     * 장바구니 항목 ID로 조회
     */
    Optional<CartItem> findById(String id);

    /**
     * 장바구니 ID로 항목 목록 조회
     */
    List<CartItem> findByCartId(String cartId);

    /**
     * 장바구니 ID와 상품 ID로 조회 (중복 확인용)
     */
    Optional<CartItem> findByCartIdAndProductId(String cartId, String productId);

    /**
     * 모든 장바구니 항목 조회
     */
    List<CartItem> findAll();

    /**
     * 장바구니 항목 저장 (생성 및 업데이트)
     */
    CartItem save(CartItem cartItem);

    /**
     * 장바구니 항목 삭제
     */
    void deleteById(String id);

    /**
     * 장바구니의 모든 항목 삭제
     */
    void deleteByCartId(String cartId);

    /**
     * 장바구니 항목 존재 여부 확인
     */
    boolean existsById(String id);
}
