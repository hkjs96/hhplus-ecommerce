package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    Optional<CartItem> findById(String id);

    List<CartItem> findByCartId(String cartId);

    Optional<CartItem> findByCartIdAndProductId(String cartId, String productId);

    List<CartItem> findAll();

    CartItem save(CartItem cartItem);

    void deleteById(String id);

    void deleteByCartId(String cartId);

    boolean existsById(String id);

    /**
     * ID로 CartItem을 조회하고, 존재하지 않으면 예외를 발생시킵니다.
     *
     * @param id CartItem ID
     * @return CartItem 엔티티
     * @throws BusinessException 장바구니 아이템을 찾을 수 없을 때
     */
    default CartItem findByIdOrThrow(String id) {
        return findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_ITEM_NOT_FOUND,
                "장바구니 아이템을 찾을 수 없습니다. cartItemId: " + id
            ));
    }
}
