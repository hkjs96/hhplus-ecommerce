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
        log.info("Adding item to cart for user: {}, product: {}", request.userId(), request.productId());

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(request.userId());

        // 2. 상품 검증 및 재고 확인
        Product product = productRepository.findByIdOrThrow(request.productId());

        if (product.getStock() < request.quantity()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                    product.getName(), request.quantity(), product.getStock())
            );
        }

        // 3. 장바구니 조회 또는 생성
        Cart cart = cartRepository.findByUserId(request.userId())
            .orElseGet(() -> {
                Cart newCart = Cart.create(request.userId());
                return cartRepository.save(newCart);
            });

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
                    CartItem newItem = CartItem.create(
                        cart,      // Cart 엔티티 직접 전달
                        product,   // Product 엔티티 직접 전달
                        request.quantity()
                    );
                    // 양방향 관계 동기화 (선택적)
                    cart.addCartItem(newItem);
                    cartItemRepository.save(newItem);
                    log.debug("Created new cart item: {}", newItem.getId());
                }
            );

        // 5. 장바구니 저장 (updatedAt은 JPA Auditing이 자동 처리)
        cartRepository.save(cart);

        // 6. 장바구니 조회 후 반환
        return getCartUseCase.execute(request.userId());
    }
}
