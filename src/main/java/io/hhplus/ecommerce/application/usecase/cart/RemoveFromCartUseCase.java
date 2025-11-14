package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.dto.DeleteCartItemRequest;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class RemoveFromCartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public void execute(DeleteCartItemRequest request) {
        log.info("Removing item from cart for user: {}, product: {}", request.userId(), request.productId());

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(request.userId());

        // 2. 장바구니 조회
        Cart cart = cartRepository.findByUserId(request.userId())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_NOT_FOUND,
                "장바구니를 찾을 수 없습니다. userId: " + request.userId()
            ));

        // 3. 장바구니 아이템 조회
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(
            cart.getId(),
            request.productId()
        ).orElseThrow(() -> new BusinessException(
            ErrorCode.CART_ITEM_NOT_FOUND,
            "장바구니에 해당 상품이 없습니다. productId: " + request.productId()
        ));

        // 4. 아이템 삭제
        cartItemRepository.deleteById(cartItem.getId());
        log.debug("Deleted cart item: {}", cartItem.getId());

        // 5. 장바구니 업데이트 시간 갱신
        cart.updateTimestamp();
        cartRepository.save(cart);
    }
}
