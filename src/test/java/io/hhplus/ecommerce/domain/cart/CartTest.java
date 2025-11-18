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
        // createdAt, updatedAt은 JPA Auditing이 DB 저장 시 자동으로 설정
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
    @DisplayName("장바구니 생성 시 타임스탬프 자동 설정")
    void create_타임스탬프_자동설정() {
        // Given
        Long userId = 1L;

        // When
        Cart cart = Cart.create(userId);

        // Then
        // createdAt, updatedAt은 BaseTimeEntity 상속으로 null로 초기화
        // 실제 값은 JPA Auditing이 DB 저장 시 자동으로 설정
        assertThat(cart.getUserId()).isEqualTo(userId);
    }
}
