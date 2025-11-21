package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.dto.CartItemResponse;
import io.hhplus.ecommerce.application.cart.dto.UpdateCartItemRequest;
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
public class UpdateCartItemUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartItemResponse execute(UpdateCartItemRequest request) {
        log.info("Updating cart item for user: {}, product: {}, new quantity: {}",
            request.userId(), request.productId(), request.quantity());

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

        // 4. 수량이 0 이하면 아이템 삭제
        if (request.quantity() <= 0) {
            cartItemRepository.deleteById(cartItem.getId());
            log.debug("Deleted cart item: {}", cartItem.getId());
            return CartItemResponse.forUpdate(request.productId(), 0, 0L);
        }

        // 5. 상품 재고 확인
        Product product = productRepository.findByIdOrThrow(request.productId());

        if (product.getStock() < request.quantity()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                    product.getName(), request.quantity(), product.getStock())
            );
        }

        // 6. 수량 업데이트
        cartItem.updateQuantity(request.quantity());
        cartItemRepository.save(cartItem);

        // 7. 장바구니 저장 (updatedAt은 JPA Auditing이 자동 처리)
        cartRepository.save(cart);

        Long subtotal = product.getPrice() * request.quantity();
        log.debug("Updated cart item: {}, quantity: {}, subtotal: {}", cartItem.getId(), request.quantity(), subtotal);
        return CartItemResponse.forUpdate(request.productId(), request.quantity(), subtotal);
    }
}
