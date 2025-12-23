package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.CartLockManager;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class RemoveFromCartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CartLockManager cartLockManager;

    /**
     * 장바구니 아이템 삭제 (캐시 무효화)
     *
     * @CacheEvict: 장바구니 변경 시 캐시 무효화
     * - value = "carts": 장바구니 캐시
     * - key = "#request.userId()": 해당 사용자의 캐시만 삭제
     */
    @Transactional
    @CacheEvict(value = "carts", key = "#request.userId()")
    public void execute(DeleteCartItemRequest request) {
        cartLockManager.withLock(request.userId(), () -> {
            log.info("Removing item from cart for user: {}, product: {}", request.userId(), request.productId());

            // 1. 사용자 검증
            userRepository.findByIdOrThrow(request.userId());

            // 2. 장바구니 조회
            Cart cart = cartRepository.findByUserIdForUpdate(request.userId())
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
            try {
                // delete(entity) 사용 시 @Version 컬럼을 포함해 삭제 쿼리를 날려 동시성 충돌을 감지한다.
                cartItemRepository.delete(cartItem);
            } catch (ObjectOptimisticLockingFailureException | EmptyResultDataAccessException e) {
                // 다른 트랜잭션이 먼저 삭제한 경우: 사용자 입장에서는 이미 없는 상태이므로 NOT_FOUND로 응답
                throw new BusinessException(
                    ErrorCode.CART_ITEM_NOT_FOUND,
                    "장바구니에 해당 상품이 없습니다. productId: " + request.productId()
                );
            }
            log.debug("Deleted cart item: {}", cartItem.getId());

            // 5. 장바구니 저장 (updatedAt은 JPA Auditing이 자동 처리)
            cartRepository.save(cart);
        });
    }
}
