package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.dto.CartItemResponse;
import io.hhplus.ecommerce.application.cart.dto.CartResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
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

import java.util.List;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartResponse execute(Long userId) {
        log.info("Getting cart for user: {}", userId);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. 장바구니 조회
        Cart cart = cartRepository.findByUserId(userId).orElse(null);

        if (cart == null) {
            log.debug("No cart found for user: {}", userId);
            return CartResponse.of(userId, List.of());
        }

        // 3. 장바구니 아이템 조회 및 상품 정보 매핑
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

        List<CartItemResponse> itemResponses = cartItems.stream()
            .map(cartItem -> {
                Product product = productRepository.findByIdOrThrow(cartItem.getProductId());
                return CartItemResponse.of(cartItem, product);
            })
            .toList();

        log.debug("Found {} items in cart for user: {}", itemResponses.size(), userId);
        return CartResponse.of(userId, itemResponses);
    }
}
