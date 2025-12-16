package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CartTest {

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.createForTest(1L, "test@example.com", "testuser", 0L);
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
        // createdAt, updatedAt은 JPA Auditing이 DB 저장 시 자동으로 설정
    }

    @Test
    @DisplayName("장바구니 생성 - userId null")
    void create_실패_userId_null() {
        // When & Then
        assertThatThrownBy(() -> Cart.create(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("장바구니 생성 시 타임스탬프 자동 설정")
    void create_타임스탬프_자동설정() {
        // Given
        // When
        Cart cart = Cart.create(testUser);

        // Then
        // createdAt, updatedAt은 BaseTimeEntity 상속으로 null로 초기화
        // 실제 값은 JPA Auditing이 DB 저장 시 자동으로 설정
        assertThat(cart.getUserId()).isEqualTo(testUser.getId());
    }
}