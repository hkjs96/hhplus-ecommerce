package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    Optional<CartItem> findById(Long id);

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    List<CartItem> findAll();

    CartItem save(CartItem cartItem);

    void deleteById(Long id);

    void deleteByCartId(Long cartId);

    boolean existsById(Long id);

    /**
     * ID로 CartItem을 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id CartItem ID (BIGINT)
     * @return CartItem 엔티티
     * @throws BusinessException 장바구니 아이템을 찾을 수 없을 때
     */
    default CartItem findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_ITEM_NOT_FOUND,
                "장바구니 아이템을 찾을 수 없습니다. cartItemId: " + id
            ));
    }
}
