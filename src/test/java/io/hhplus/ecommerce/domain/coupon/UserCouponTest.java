package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class UserCouponTest {

    @Test
    @DisplayName("사용자 쿠폰 생성 성공")
    void create_성공() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        // When
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);

        // Then
        assertThat(userCoupon.getId()).isNull(); // ID는 JPA에 의해 자동 생성됨
        assertThat(userCoupon.getUserId()).isEqualTo(userId);
        assertThat(userCoupon.getCouponId()).isEqualTo(couponId);
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(userCoupon.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(userCoupon.getIssuedAt()).isNotNull();
        assertThat(userCoupon.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("사용자 쿠폰 생성 실패 - 사용자 ID가 null")
    void create_사용자ID_null_예외발생() {
        // Given
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        // When & Then
        assertThatThrownBy(() -> UserCoupon.create(null, couponId, expiresAt))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 쿠폰 생성 실패 - 쿠폰 ID가 null")
    void create_쿠폰ID_null_예외발생() {
        // Given
        Long userId = 1L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        // When & Then
        assertThatThrownBy(() -> UserCoupon.create(userId, null, expiresAt))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("사용자 쿠폰 생성 실패 - 만료일이 null")
    void create_만료일_null_예외발생() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;

        // When & Then
        assertThatThrownBy(() -> UserCoupon.create(userId, couponId, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 사용 성공")
    void use_성공() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);

        // When
        userCoupon.use();

        // Then
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(userCoupon.getUsedAt()).isNotNull();
        assertThat(userCoupon.isUsed()).isTrue();
        assertThat(userCoupon.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 사용 실패 - 이미 사용된 쿠폰")
    void use_이미사용됨_예외발생() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);
        userCoupon.use();  // 먼저 사용

        // When & Then - 재사용 시도
        assertThatThrownBy(() -> userCoupon.use())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COUPON);
    }

    @Test
    @DisplayName("쿠폰 사용 실패 - 만료된 쿠폰")
    void use_만료됨_예외발생() {
        // Given - 이미 만료된 쿠폰 생성
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().minusDays(1);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);

        // When & Then
        assertThatThrownBy(() -> userCoupon.use())
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_COUPON);
    }

    @Test
    @DisplayName("쿠폰 만료 처리 성공")
    void expire_성공() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);

        // When
        userCoupon.expire();

        // Then
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(userCoupon.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 만료 처리 - 이미 사용된 쿠폰은 상태 변경 안 됨")
    void expire_이미사용됨_상태유지() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);
        userCoupon.use();  // 먼저 사용

        // When
        userCoupon.expire();

        // Then - USED 상태 유지
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부 확인 - 사용 가능")
    void isAvailable_사용가능() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);

        // When & Then
        assertThat(userCoupon.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부 확인 - 만료됨")
    void isAvailable_만료됨() {
        // Given - 이미 만료된 쿠폰
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().minusDays(1);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);

        // When & Then
        assertThat(userCoupon.isAvailable()).isFalse();
        assertThat(userCoupon.isExpired()).isTrue();
    }

    @Test
    @DisplayName("쿠폰 사용 가능 여부 확인 - 사용됨")
    void isAvailable_사용됨() {
        // Given
        Long userId = 1L;
        Long couponId = 2L;
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, expiresAt);
        userCoupon.use();

        // When & Then
        assertThat(userCoupon.isAvailable()).isFalse();
        assertThat(userCoupon.isUsed()).isTrue();
    }
}
