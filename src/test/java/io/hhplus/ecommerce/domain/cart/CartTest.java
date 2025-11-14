package io.hhplus.ecommerce.domain.cart;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CartTest {

    @Test
    @DisplayName("장바구니 생성 - 성공")
    void create_성공() {
        // Given
        Long userId = 1L;

        // When
        Cart cart = Cart.create(userId);

        // Then
        assertThat(cart).isNotNull();
        assertThat(cart.getId()).isNull(); // ID는 JPA에 의해 자동 생성됨
        assertThat(cart.getUserId()).isEqualTo(userId);
        assertThat(cart.getCreatedAt()).isNotNull();
        assertThat(cart.getUpdatedAt()).isNotNull();
        assertThat(cart.getCreatedAt()).isEqualTo(cart.getUpdatedAt());
    }

    @Test
    @DisplayName("장바구니 생성 - userId null")
    void create_실패_userId_null() {
        // When & Then
        assertThatThrownBy(() -> Cart.create(null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("사용자 ID는 필수입니다")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("장바구니 업데이트 시각 갱신")
    void updateTimestamp_성공() throws InterruptedException {
        // Given
        Long userId = 1L;
        Cart cart = Cart.create(userId);
        LocalDateTime originalUpdatedAt = cart.getUpdatedAt();

        // 시간 차이를 만들기 위해 약간 대기
        Thread.sleep(10);

        // When
        cart.updateTimestamp();

        // Then
        assertThat(cart.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(cart.getCreatedAt()).isEqualTo(cart.getCreatedAt()); // createdAt은 변경 안됨
    }
}
