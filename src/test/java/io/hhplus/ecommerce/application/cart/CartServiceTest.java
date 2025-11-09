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
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private User testUser;
    private Product testProduct;
    private Cart testCart;

    @BeforeEach
    void setUp() {
        testUser = User.create("U001", "kim@example.com", "김항해");
        testUser.charge(100000L);
        testProduct = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 10);
        testCart = Cart.create("CART-U001", "U001");
    }

    // ====================================
    // 장바구니에 상품 추가
    // ====================================

    @Test
    @DisplayName("장바구니에 상품 추가 - 성공 (신규 상품)")
    void addItemToCart_성공_신규상품() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(cartItemRepository.findByCartId(anyString())).thenReturn(List.of());

        // When
        CartResponse response = cartService.addItemToCart(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("U001");
        verify(cartItemRepository).save(any(CartItem.class));
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 성공 (중복 상품, 수량 증가)")
    void addItemToCart_성공_중복상품_수량증가() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("U001", "P001", 2);
        CartItem existingItem = CartItem.create("ITEM-001", "CART-U001", "P001", 3);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId(anyString(), eq("P001")))
            .thenReturn(Optional.of(existingItem));
        when(cartItemRepository.findByCartId(anyString())).thenReturn(List.of());

        // When
        CartResponse response = cartService.addItemToCart(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(existingItem.getQuantity()).isEqualTo(5); // 3 + 2
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 장바구니 자동 생성")
    void addItemToCart_성공_장바구니_자동생성() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("U001", "P001", 2);
        Cart newCart = Cart.create("CART-U001", "U001");
        CartItem newItem = CartItem.create("ITEM-NEW", "CART-U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);

        // First call returns empty (no cart exists), second call returns the created cart
        when(cartRepository.findByUserId("U001"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(newCart));

        when(cartRepository.save(any(Cart.class))).thenReturn(newCart);
        when(cartItemRepository.findByCartIdAndProductId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(newItem);
        when(cartItemRepository.findByCartId(anyString())).thenReturn(List.of(newItem));

        // When
        CartResponse response = cartService.addItemToCart(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo("P001");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        verify(cartRepository, times(2)).save(any(Cart.class)); // 생성 + 업데이트
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 실패 (사용자 없음)")
    void addItemToCart_실패_사용자없음() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("INVALID", "P001", 2);
        when(userRepository.findByIdOrThrow("INVALID")).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // When & Then
        assertThatThrownBy(() -> cartService.addItemToCart(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(productRepository, never()).findByIdOrThrow(anyString());
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 실패 (상품 없음)")
    void addItemToCart_실패_상품없음() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("U001", "INVALID", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(productRepository.findByIdOrThrow("INVALID")).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다."));

        // When & Then
        assertThatThrownBy(() -> cartService.addItemToCart(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 실패 (재고 부족)")
    void addItemToCart_실패_재고부족() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("U001", "P001", 20); // 재고 10개

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);

        // When & Then
        assertThatThrownBy(() -> cartService.addItemToCart(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    @DisplayName("장바구니에 상품 추가 - 실패 (중복 상품 추가 시 재고 부족)")
    void addItemToCart_실패_중복상품_재고부족() {
        // Given
        AddCartItemRequest request = new AddCartItemRequest("U001", "P001", 8);
        CartItem existingItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId(anyString(), eq("P001")))
            .thenReturn(Optional.of(existingItem));

        // When & Then (5 + 8 = 13 > 재고 10)
        assertThatThrownBy(() -> cartService.addItemToCart(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ====================================
    // 장바구니 조회
    // ====================================

    @Test
    @DisplayName("장바구니 조회 - 성공 (항목 있음)")
    void getCart_성공_항목있음() {
        // Given
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartId("CART-U001")).thenReturn(List.of(cartItem));
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);

        // When
        CartResponse response = cartService.getCart("U001");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("U001");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo("P001");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getItems().get(0).getStockAvailable()).isTrue(); // 재고 10 >= 수량 2
        assertThat(response.getTotalAmount()).isEqualTo(890000L * 2);
    }

    @Test
    @DisplayName("장바구니 조회 - 성공 (빈 장바구니)")
    void getCart_성공_빈장바구니() {
        // Given
        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.empty());

        // When
        CartResponse response = cartService.getCart("U001");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("U001");
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalAmount()).isZero();
    }

    @Test
    @DisplayName("장바구니 조회 - 재고 부족 상품 표시")
    void getCart_성공_재고부족_표시() {
        // Given
        Product lowStockProduct = Product.create("P001", "노트북", "고성능 노트북", 890000L, "전자제품", 1);
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 5); // 수량 5 > 재고 1

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartId("CART-U001")).thenReturn(List.of(cartItem));
        when(productRepository.findByIdOrThrow("P001")).thenReturn(lowStockProduct);

        // When
        CartResponse response = cartService.getCart("U001");

        // Then
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getStockAvailable()).isFalse(); // 재고 부족 표시
    }

    @Test
    @DisplayName("장바구니 조회 - 실패 (사용자 없음)")
    void getCart_실패_사용자없음() {
        // Given
        when(userRepository.findByIdOrThrow("INVALID")).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // When & Then
        assertThatThrownBy(() -> cartService.getCart("INVALID"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ====================================
    // 장바구니 상품 수량 변경
    // ====================================

    @Test
    @DisplayName("장바구니 상품 수량 변경 - 성공")
    void updateCartItem_성공() {
        // Given
        UpdateCartItemRequest request = new UpdateCartItemRequest("U001", "P001", 5);
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId("CART-U001", "P001"))
            .thenReturn(Optional.of(cartItem));
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);

        // When
        CartItemResponse response = cartService.updateCartItem(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProductId()).isEqualTo("P001");
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getSubtotal()).isEqualTo(890000L * 5);
        assertThat(cartItem.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    @DisplayName("장바구니 상품 수량 변경 - 수량 0 이하 시 삭제")
    void updateCartItem_성공_수량0_삭제() {
        // Given
        UpdateCartItemRequest request = new UpdateCartItemRequest("U001", "P001", 0);
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId("CART-U001", "P001"))
            .thenReturn(Optional.of(cartItem));

        // When
        CartItemResponse response = cartService.updateCartItem(request);

        // Then
        assertThat(response.getQuantity()).isZero();
        verify(cartItemRepository).deleteById("ITEM-001");
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("장바구니 상품 수량 변경 - 실패 (장바구니 없음)")
    void updateCartItem_실패_장바구니없음() {
        // Given
        UpdateCartItemRequest request = new UpdateCartItemRequest("U001", "P001", 5);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cartService.updateCartItem(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니 상품 수량 변경 - 실패 (장바구니 항목 없음)")
    void updateCartItem_실패_항목없음() {
        // Given
        UpdateCartItemRequest request = new UpdateCartItemRequest("U001", "P001", 5);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId("CART-U001", "P001"))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cartService.updateCartItem(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니 상품 수량 변경 - 실패 (재고 부족)")
    void updateCartItem_실패_재고부족() {
        // Given
        UpdateCartItemRequest request = new UpdateCartItemRequest("U001", "P001", 20); // 재고 10개
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId("CART-U001", "P001"))
            .thenReturn(Optional.of(cartItem));
        when(productRepository.findByIdOrThrow("P001")).thenReturn(testProduct);

        // When & Then
        assertThatThrownBy(() -> cartService.updateCartItem(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // ====================================
    // 장바구니 상품 삭제
    // ====================================

    @Test
    @DisplayName("장바구니 상품 삭제 - 성공")
    void deleteCartItem_성공() {
        // Given
        DeleteCartItemRequest request = new DeleteCartItemRequest("U001", "P001");
        CartItem cartItem = CartItem.create("ITEM-001", "CART-U001", "P001", 2);

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId("CART-U001", "P001"))
            .thenReturn(Optional.of(cartItem));

        // When
        cartService.deleteCartItem(request);

        // Then
        verify(cartItemRepository).deleteById("ITEM-001");
        verify(cartRepository).save(testCart);
    }

    @Test
    @DisplayName("장바구니 상품 삭제 - 실패 (장바구니 없음)")
    void deleteCartItem_실패_장바구니없음() {
        // Given
        DeleteCartItemRequest request = new DeleteCartItemRequest("U001", "P001");

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cartService.deleteCartItem(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    @DisplayName("장바구니 상품 삭제 - 실패 (장바구니 항목 없음)")
    void deleteCartItem_실패_항목없음() {
        // Given
        DeleteCartItemRequest request = new DeleteCartItemRequest("U001", "P001");

        when(userRepository.findByIdOrThrow("U001")).thenReturn(testUser);
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartIdAndProductId("CART-U001", "P001"))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cartService.deleteCartItem(request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    // ====================================
    // 장바구니 비우기
    // ====================================

    @Test
    @DisplayName("장바구니 비우기 - 성공")
    void clearCart_성공() {
        // Given
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.of(testCart));

        // When
        cartService.clearCart("U001");

        // Then
        verify(cartItemRepository).deleteByCartId("CART-U001");
        verify(cartRepository).save(testCart);
    }

    @Test
    @DisplayName("장바구니 비우기 - 장바구니 없어도 예외 없음")
    void clearCart_장바구니없음_예외없음() {
        // Given
        when(cartRepository.findByUserId("U001")).thenReturn(Optional.empty());

        // When
        cartService.clearCart("U001");

        // Then
        verify(cartItemRepository, never()).deleteByCartId(anyString());
        verify(cartRepository, never()).save(any(Cart.class));
    }
}
