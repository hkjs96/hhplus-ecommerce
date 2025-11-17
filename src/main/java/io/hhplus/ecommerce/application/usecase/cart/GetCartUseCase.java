package io.hhplus.ecommerce.application.usecase.cart;

import io.hhplus.ecommerce.application.cart.dto.CartItemResponse;
import io.hhplus.ecommerce.application.cart.dto.CartResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.cart.CartWithItemsProjection;
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
    private final UserRepository userRepository;

    public CartResponse execute(Long userId) {
        log.info("Getting cart for user: {} using optimized Native Query", userId);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. Native Query로 장바구니 + 아이템 + 상품 조회 (Single Query)
        List<CartWithItemsProjection> projectionsFromDb =
            cartRepository.findCartWithItemsByUserId(userId);

        if (projectionsFromDb.isEmpty()) {
            log.debug("No cart found for user: {}", userId);
            return CartResponse.of(userId, List.of());
        }

        // 3. Projection → CartItemResponse 변환
        List<CartItemResponse> itemResponses = projectionsFromDb.stream()
            .map(proj -> {
                Long subtotal = proj.getPrice() * proj.getQuantity();
                Boolean stockAvailable = proj.getStock() >= proj.getQuantity();

                return new CartItemResponse(
                    proj.getProductId(),
                    proj.getProductName(),
                    proj.getPrice(),
                    proj.getQuantity(),
                    subtotal,
                    stockAvailable
                );
            })
            .toList();

        log.info("Found {} items in cart for user: {} using optimized query", itemResponses.size(), userId);
        return CartResponse.of(userId, itemResponses);
    }
}
