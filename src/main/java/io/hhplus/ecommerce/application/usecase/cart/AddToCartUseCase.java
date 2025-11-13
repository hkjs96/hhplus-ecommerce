package io.hhplus.ecommerce.application.usecase.cart;

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

    @Transactional
    public CartResponse execute(AddCartItemRequest request) {
        log.info("Adding item to cart for user: {}, product: {}", request.getUserId(), request.getProductId());

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(request.getUserId());

        // 2. 상품 검증 및 재고 확인
        Product product = productRepository.findByIdOrThrow(request.getProductId());

        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                    product.getName(), request.getQuantity(), product.getStock())
            );
        }

        // 3. 장바구니 조회 또는 생성
        Cart cart = cartRepository.findByUserId(request.getUserId())
            .orElseGet(() -> {
                Cart newCart = Cart.create(request.getUserId());
                return cartRepository.save(newCart);
            });

        // 4. 장바구니 아이템 추가 또는 수량 증가
        cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
            .ifPresentOrElse(
                existingItem -> {
                    int newQuantity = existingItem.getQuantity() + request.getQuantity();

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
                    CartItem newItem = CartItem.create(
                        cart.getId(),
                        request.getProductId(),
                        request.getQuantity()
                    );
                    cartItemRepository.save(newItem);
                    log.debug("Created new cart item: {}", newItem.getId());
                }
            );

        // 5. 장바구니 업데이트 시간 갱신
        cart.updateTimestamp();
        cartRepository.save(cart);

        // 6. 장바구니 조회 후 반환
        return getCartUseCase.execute(request.getUserId());
    }
}
