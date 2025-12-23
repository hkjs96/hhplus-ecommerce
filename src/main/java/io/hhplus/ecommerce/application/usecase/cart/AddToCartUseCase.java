package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.CartLockManager;
import io.hhplus.ecommerce.application.cart.dto.AddCartItemRequest;
import io.hhplus.ecommerce.application.cart.dto.CartResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class AddToCartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final GetCartUseCase getCartUseCase;
    private final CartLockManager cartLockManager;

    /**
     * 장바구니 아이템 추가 (캐시 무효화)
     *
     * @CacheEvict: 장바구니 변경 시 캐시 무효화
     * - value = "carts": 장바구니 캐시
     * - key = "#request.userId()": 해당 사용자의 캐시만 삭제
     *
     * 트랜잭션 커밋 후 캐시 삭제 (CacheConfig의 transactionAware=true)
     * - 롤백 시 캐시 무효화되지 않음
     */
    @Transactional
    @CacheEvict(value = "carts", key = "#request.userId()")
    public CartResponse execute(AddCartItemRequest request) {
        return cartLockManager.withLock(request.userId(), () -> {
            log.info("Adding item to cart for user: {}, product: {}", request.userId(), request.productId());

            // 1. 사용자 검증
            userRepository.findByIdOrThrow(request.userId());

            // 2. 장바구니를 비관적 락으로 확보 (동일 사용자 경합 시 순서 보장)
            Cart cart = cartRepository.findByUserIdForUpdate(request.userId())
                .orElseGet(() -> createCartIfAbsent(request.userId()));

            // 3. 상품 조회
            Product product = productRepository.findByIdOrThrow(request.productId());

            // 4. 장바구니 아이템 추가 또는 수량 증가 (개선: Cart 엔티티 직접 참조)
            cartItemRepository.findByCartIdAndProductId(cart.getId(), request.productId())
                .ifPresentOrElse(
                    existingItem -> {
                        int newQuantity = existingItem.getQuantity() + request.quantity();

                        if (product.getStock() < newQuantity) {
                            throw new BusinessException(
                                ErrorCode.INSUFFICIENT_STOCK,
                                String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                                    product.getName(), newQuantity, product.getStock())
                            );
                        }

                        existingItem.updateQuantity(newQuantity);
                        cartItemRepository.save(existingItem);
                        log.debug("Updated existing cart item: {}, new quantity: {}", existingItem.getId(), newQuantity);
                    },
                    () -> {
                        // 개선: Cart 엔티티 직접 참조 (cart.getId() → cart)
                        try {
                            if (product.getStock() < request.quantity()) {
                                throw new BusinessException(
                                    ErrorCode.INSUFFICIENT_STOCK,
                                    String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                                        product.getName(), request.quantity(), product.getStock())
                                );
                            }

                            CartItem newItem = CartItem.create(
                                cart,      // Cart 엔티티 직접 전달
                                product,   // Product 엔티티 직접 전달
                                request.quantity()
                            );
                            // 양방향 관계 동기화 (선택적)
                            cart.addCartItem(newItem);
                            cartItemRepository.save(newItem);
                            log.debug("Created new cart item: {}", newItem.getId());
                        } catch (DataIntegrityViolationException e) {
                            // 동시 요청이 동일 상품을 추가하려다 유니크 제약으로 충돌한 경우: 재조회 후 수량 합산
                            CartItem existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.productId())
                                .orElseThrow(() -> e); // 극단적으로 없으면 기존 예외 던짐

                            int newQuantity = existing.getQuantity() + request.quantity();
                            if (product.getStock() < newQuantity) {
                                throw new BusinessException(
                                    ErrorCode.INSUFFICIENT_STOCK,
                                    String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                                        product.getName(), newQuantity, product.getStock())
                                );
                            }

                            existing.updateQuantity(newQuantity);
                            cartItemRepository.save(existing);
                            log.debug("Recovered from duplicate insert by merging quantity. cartItem: {}, new quantity: {}", existing.getId(), newQuantity);
                        }
                    }
                );

            // 5. 장바구니 저장 (updatedAt은 JPA Auditing이 자동 처리)
            cartRepository.save(cart);

            // 6. 장바구니 조회 후 반환
            return getCartUseCase.execute(request.userId());
        });
    }

    /**
     * 동시 생성 충돌 시에도 단일 Cart를 보장하기 위한 재시도 헬퍼
     */
    private Cart createCartIfAbsent(Long userId) {
        try {
            return cartRepository.save(Cart.create(userId));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE (user_id) 충돌 → 이미 생성된 Cart를 비관적 락으로 재조회
            return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> e);
        }
    }
}
