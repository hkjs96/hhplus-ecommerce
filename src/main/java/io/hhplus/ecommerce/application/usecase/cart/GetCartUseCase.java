package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.dto.CartItemResponse;
import io.hhplus.ecommerce.application.cart.dto.CartResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.persistence.cart.JpaCartItemRepository;
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
    private final JpaCartItemRepository cartItemRepository;  // Fetch Join 메서드 사용
    private final UserRepository userRepository;

    public CartResponse execute(Long userId) {
        log.info("Getting cart for user: {} using Fetch Join", userId);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. Cart 조회
        Cart cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> {
                log.debug("No cart found for user: {}", userId);
                return null;
            });

        if (cart == null) {
            return CartResponse.of(userId, List.of());
        }

        // 3. Fetch Join으로 CartItem + Product 한 번에 조회
        //    한 번의 JOIN 쿼리로 모든 데이터 로딩, N+1 문제 완전 해결
        List<CartItem> cartItems = cartItemRepository.findByCartIdWithProduct(cart.getId());

        if (cartItems.isEmpty()) {
            log.debug("Empty cart for user: {}", userId);
            return CartResponse.of(userId, List.of());
        }

        // 4. CartItem Entity → DTO 변환
        List<CartItemResponse> itemResponses = cartItems.stream()
            .map(item -> {
                Product product = item.getProduct();  // Fetch Join으로 이미 로딩됨 (추가 쿼리 X)
                Long subtotal = product.getPrice() * item.getQuantity();
                Boolean stockAvailable = product.getStock() >= item.getQuantity();

                return new CartItemResponse(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    item.getQuantity(),
                    subtotal,
                    stockAvailable
                );
            })
            .toList();

        log.info("Found {} items in cart for user: {} using Fetch Join (single query)", itemResponses.size(), userId);
        return CartResponse.of(userId, itemResponses);
    }
}
