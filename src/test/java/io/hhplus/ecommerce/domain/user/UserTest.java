package io.hhplus.ecommerce.domain.user;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

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

    @Test
    @DisplayName("사용자 생성 성공")
    void create_성공() {
        // When
        User user = User.create("U001", "test@example.com", "테스터");

        // Then
        assertThat(user.getId()).isEqualTo("U001");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getUsername()).isEqualTo("테스터");
        assertThat(user.getBalance()).isEqualTo(0L);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사용자 생성 실패 - 이메일이 null")
    void create_이메일null_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", null, "테스터"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 생성 실패 - 이메일이 빈 문자열")
    void create_이메일빈문자열_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", "", "테스터"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 생성 실패 - 이메일이 공백")
    void create_이메일공백_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", "   ", "테스터"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 생성 실패 - 이메일 형식이 잘못됨 (@없음)")
    void create_이메일형식오류_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", "testexample.com", "테스터"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 생성 실패 - 사용자명이 null")
    void create_사용자명null_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", "test@example.com", null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 생성 실패 - 사용자명이 빈 문자열")
    void create_사용자명빈문자열_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", "test@example.com", ""))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 생성 실패 - 사용자명이 공백")
    void create_사용자명공백_예외발생() {
        // When & Then
        assertThatThrownBy(() -> User.create("U001", "test@example.com", "   "))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("포인트 충전 실패 - 충전 금액이 null")
    void charge_금액null_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");

        // When & Then
        assertThatThrownBy(() -> user.charge(null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("포인트 차감 실패 - 차감 금액이 null")
    void deduct_금액null_예외발생() {
        // Given
        User user = User.create("U001", "test@example.com", "테스터");
        user.charge(10000L);

        // When & Then
        assertThatThrownBy(() -> user.deduct(null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }
}
