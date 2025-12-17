package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CartTest {

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        testUser = User.createForTest(1L, "test@example.com", "testuser", 0L);
        testProduct1 = Product.create("P001", "상품1", "설명1", 10000L, "전자기기", 100);
        testProduct2 = Product.create("P002", "상품2", "설명2", 20000L, "의류", 50);
    }

    @Test
    @DisplayName("장바구니 생성 - 성공")
    void create_성공() {
        // Given
        // When
        Cart cart = Cart.create(testUser);

        // Then
        assertThat(cart).isNotNull();
        assertThat(cart.getId()).isNull(); // ID는 JPA에 의해 자동 생성됨
        assertThat(cart.getUserId()).isEqualTo(testUser.getId());
        assertThat(cart.getCartItems()).isEmpty();
        // createdAt, updatedAt은 JPA Auditing이 DB 저장 시 자동으로 설정
    }

    @Test
    @DisplayName("장바구니 생성 - User가 null")
    void create_실패_user_null() {
        // When & Then
        assertThatThrownBy(() -> Cart.create(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("장바구니 생성 - userId가 null")
    void create_실패_userId_null() {
        // Given
        User userWithNullId = User.createForTest(null, "test@example.com", "testuser", 0L);

        // When & Then
        assertThatThrownBy(() -> Cart.create(userWithNullId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT)
            .hasMessageContaining("사용자 ID는 필수입니다");
    }

    @Test
    @DisplayName("CartItem 추가 - 양방향 관계 동기화 확인")
    void addCartItem_성공() {
        // Given
        Cart cart = Cart.create(testUser);
        CartItem cartItem = CartItem.create(cart, testProduct1, 5);

        // When
        cart.addCartItem(cartItem);

        // Then
        assertThat(cart.getCartItems()).hasSize(1);
        assertThat(cart.getCartItems()).contains(cartItem);
        assertThat(cartItem.getCart()).isEqualTo(cart); // 양방향 관계 확인
    }

    @Test
    @DisplayName("여러 CartItem 추가")
    void addCartItem_여러개() {
        // Given
        Cart cart = Cart.create(testUser);
        CartItem cartItem1 = CartItem.create(cart, testProduct1, 3);
        CartItem cartItem2 = CartItem.create(cart, testProduct2, 2);

        // When
        cart.addCartItem(cartItem1);
        cart.addCartItem(cartItem2);

        // Then
        assertThat(cart.getCartItems()).hasSize(2);
        assertThat(cart.getCartItems()).containsExactly(cartItem1, cartItem2);
        assertThat(cartItem1.getCart()).isEqualTo(cart);
        assertThat(cartItem2.getCart()).isEqualTo(cart);
    }

    @Test
    @DisplayName("CartItem 제거 - 양방향 관계 동기화 확인")
    void removeCartItem_성공() {
        // Given
        Cart cart = Cart.create(testUser);
        CartItem cartItem = CartItem.create(cart, testProduct1, 5);
        cart.addCartItem(cartItem);

        // When
        cart.removeCartItem(cartItem);

        // Then
        assertThat(cart.getCartItems()).isEmpty();
        assertThat(cartItem.getCart()).isNull(); // 양방향 관계 끊김 확인
    }

    @Test
    @DisplayName("CartItem 제거 - 여러 개 중 하나만 제거")
    void removeCartItem_여러개중_하나만() {
        // Given
        Cart cart = Cart.create(testUser);
        CartItem cartItem1 = CartItem.create(cart, testProduct1, 3);
        CartItem cartItem2 = CartItem.create(cart, testProduct2, 2);
        cart.addCartItem(cartItem1);
        cart.addCartItem(cartItem2);

        // When
        cart.removeCartItem(cartItem1);

        // Then
        assertThat(cart.getCartItems()).hasSize(1);
        assertThat(cart.getCartItems()).containsExactly(cartItem2);
        assertThat(cartItem1.getCart()).isNull();
        assertThat(cartItem2.getCart()).isEqualTo(cart);
    }

    @Test
    @DisplayName("빈 장바구니에서 CartItem 제거 시도 - 아무 일도 일어나지 않음")
    void removeCartItem_빈장바구니() {
        // Given
        Cart cart = Cart.create(testUser);
        CartItem cartItem = CartItem.create(cart, testProduct1, 5);

        // When
        cart.removeCartItem(cartItem);

        // Then
        assertThat(cart.getCartItems()).isEmpty();
    }
}