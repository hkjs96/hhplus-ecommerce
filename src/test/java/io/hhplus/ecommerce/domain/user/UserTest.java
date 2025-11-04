package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * User Entity 단위 테스트
 * Week 3: 핵심 비즈니스 로직 테스트 (포인트 충전/차감)
 */
class UserTest {

    @Test
    @DisplayName("포인트 충전 성공")
    void charge_성공() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        assertThat(user.getBalance()).isEqualTo(0L);

        // When
        user.charge(10000L);

        // Then
        assertThat(user.getBalance()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 충전 금액이 0")
    void charge_금액0_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");

        // When & Then
        assertThatThrownBy(() -> user.charge(0L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 충전 금액이 음수")
    void charge_금액음수_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");

        // When & Then
        assertThatThrownBy(() -> user.charge(-1000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 차감 성공")
    void deduct_성공() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(10000L);

        // When
        user.deduct(3000L);

        // Then
        assertThat(user.getBalance()).isEqualTo(7000L);
    }

    @Test
    @DisplayName("포인트 차감 실패 - 잔액 부족")
    void deduct_잔액부족_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(5000L);

        // When & Then
        assertThatThrownBy(() -> user.deduct(10000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

        // 잔액은 변경되지 않음
        assertThat(user.getBalance()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("포인트 차감 실패 - 차감 금액이 0")
    void deduct_금액0_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(10000L);

        // When & Then
        assertThatThrownBy(() -> user.deduct(0L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("포인트 차감 실패 - 차감 금액이 음수")
    void deduct_금액음수_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(10000L);

        // When & Then
        assertThatThrownBy(() -> user.deduct(-1000L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("잔액 확인 - 충분함")
    void hasEnoughBalance_충분함() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(10000L);

        // When & Then
        assertThat(user.hasEnoughBalance(5000L)).isTrue();
        assertThat(user.hasEnoughBalance(10000L)).isTrue();
    }

    @Test
    @DisplayName("잔액 확인 - 부족함")
    void hasEnoughBalance_부족함() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(10000L);

        // When & Then
        assertThat(user.hasEnoughBalance(10001L)).isFalse();
    }
}
