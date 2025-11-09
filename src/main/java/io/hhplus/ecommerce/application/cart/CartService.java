package io.hhplus.ecommerce.application.cart;

import io.hhplus.ecommerce.application.cart.dto.*;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartResponse addItemToCart(AddCartItemRequest request) {
        userRepository.findByIdOrThrow(request.getUserId());
        Product product = productRepository.findByIdOrThrow(request.getProductId());

        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                    product.getName(), request.getQuantity(), product.getStock())
            );
        }

        Cart cart = cartRepository.findByUserId(request.getUserId())
            .orElseGet(() -> {
                String cartId = "CART-" + request.getUserId();
                Cart newCart = Cart.create(cartId, request.getUserId());
                return cartRepository.save(newCart);
            });

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
                },
                () -> {
                    String itemId = UUID.randomUUID().toString();
                    CartItem newItem = CartItem.create(
                        itemId,
                        cart.getId(),
                        request.getProductId(),
                        request.getQuantity()
                    );
                    cartItemRepository.save(newItem);
                }
            );

        cart.updateTimestamp();
        cartRepository.save(cart);

        return getCart(request.getUserId());
    }

    public CartResponse getCart(String userId) {
        userRepository.findByIdOrThrow(userId);

        Cart cart = cartRepository.findByUserId(userId).orElse(null);

        if (cart == null) {
            return CartResponse.of(userId, List.of());
        }

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

        List<CartItemResponse> itemResponses = cartItems.stream()
            .map(cartItem -> {
                Product product = productRepository.findByIdOrThrow(cartItem.getProductId());
                return CartItemResponse.of(cartItem, product);
            })
            .toList();

        return CartResponse.of(userId, itemResponses);
    }

    public CartItemResponse updateCartItem(UpdateCartItemRequest request) {
        userRepository.findByIdOrThrow(request.getUserId());

        Cart cart = cartRepository.findByUserId(request.getUserId())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_NOT_FOUND,
                "장바구니를 찾을 수 없습니다. userId: " + request.getUserId()
            ));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(
            cart.getId(),
            request.getProductId()
        ).orElseThrow(() -> new BusinessException(
            ErrorCode.CART_ITEM_NOT_FOUND,
            "장바구니에 해당 상품이 없습니다. productId: " + request.getProductId()
        ));

        if (request.getQuantity() <= 0) {
            cartItemRepository.deleteById(cartItem.getId());
            return CartItemResponse.forUpdate(request.getProductId(), 0, 0L);
        }

        Product product = productRepository.findByIdOrThrow(request.getProductId());

        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                String.format("재고가 부족합니다. 상품: %s (요청: %d개, 재고: %d개)",
                    product.getName(), request.getQuantity(), product.getStock())
            );
        }

        cartItem.updateQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        cart.updateTimestamp();
        cartRepository.save(cart);

        Long subtotal = product.getPrice() * request.getQuantity();
        return CartItemResponse.forUpdate(request.getProductId(), request.getQuantity(), subtotal);
    }

    public void deleteCartItem(DeleteCartItemRequest request) {
        userRepository.findByIdOrThrow(request.getUserId());

        Cart cart = cartRepository.findByUserId(request.getUserId())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CART_NOT_FOUND,
                "장바구니를 찾을 수 없습니다. userId: " + request.getUserId()
            ));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(
            cart.getId(),
            request.getProductId()
        ).orElseThrow(() -> new BusinessException(
            ErrorCode.CART_ITEM_NOT_FOUND,
            "장바구니에 해당 상품이 없습니다. productId: " + request.getProductId()
        ));

        cartItemRepository.deleteById(cartItem.getId());

        cart.updateTimestamp();
        cartRepository.save(cart);
    }

    public void clearCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart != null) {
            cartItemRepository.deleteByCartId(cart.getId());
            cart.updateTimestamp();
            cartRepository.save(cart);
        }
    }
}
