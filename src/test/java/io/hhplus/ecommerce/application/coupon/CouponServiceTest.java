package io.hhplus.ecommerce.application.coupon;

import io.hhplus.ecommerce.application.coupon.dto.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.*;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.persistence.coupon.InMemoryCouponRepository;
import io.hhplus.ecommerce.infrastructure.persistence.coupon.InMemoryUserCouponRepository;
import io.hhplus.ecommerce.infrastructure.persistence.user.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CouponServiceTest {

    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private UserRepository userRepository;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
        userCouponRepository = new InMemoryUserCouponRepository();
        userRepository = new InMemoryUserRepository();
        couponService = new CouponService(couponRepository, userCouponRepository, userRepository);
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_성공() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        Coupon savedCoupon = couponRepository.save(coupon);
        Long couponId = savedCoupon.getId();

        IssueCouponRequest request = new IssueCouponRequest(userId);

        // When
        IssueCouponResponse response = couponService.issueCoupon(couponId, request);

        // Then
        assertThat(response.getCouponId()).isEqualTo(couponId);
        assertThat(response.getCouponName()).isEqualTo("10% 할인");
        assertThat(response.getDiscountRate()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo("AVAILABLE");
        assertThat(response.getRemainingQuantity()).isEqualTo(99);

        Coupon reloadedCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(reloadedCoupon.getIssuedQuantity()).isEqualTo(1);
        assertThat(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).isTrue();
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 사용자")
    void issueCoupon_실패_존재하지않는사용자() {
        // Given
        Long invalidUserId = 99999L;
        Long invalidCouponId = 99999L;
        IssueCouponRequest request = new IssueCouponRequest(invalidUserId);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(invalidCouponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 쿠폰")
    void issueCoupon_실패_존재하지않는쿠폰() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();
        Long invalidCouponId = 99999L;

        IssueCouponRequest request = new IssueCouponRequest(userId);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(invalidCouponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COUPON);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 만료된 쿠폰")
    void issueCoupon_실패_만료된쿠폰() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now.minusDays(10), now.minusDays(1));
        Coupon savedCoupon = couponRepository.save(coupon);
        Long couponId = savedCoupon.getId();

        IssueCouponRequest request = new IssueCouponRequest(userId);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_COUPON);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 중복 발급 (1인 1매 제한)")
    void issueCoupon_실패_중복발급() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        Coupon savedCoupon = couponRepository.save(coupon);
        Long couponId = savedCoupon.getId();

        IssueCouponRequest request = new IssueCouponRequest(userId);

        UserCoupon userCoupon = UserCoupon.create(userId, couponId, coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ISSUED_COUPON);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 수량 소진")
    void issueCoupon_실패_수량소진() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 1, now, now.plusDays(7));
        coupon.issue();
        Coupon savedCoupon = couponRepository.save(coupon);
        Long couponId = savedCoupon.getId();

        IssueCouponRequest request = new IssueCouponRequest(userId);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT);
    }

    @Test
    @DisplayName("보유 쿠폰 조회 성공")
    void getUserCoupons_성공() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        Coupon savedCoupon = couponRepository.save(coupon);
        Long couponId = savedCoupon.getId();

        UserCoupon userCoupon = UserCoupon.create(userId, couponId, coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);

        // When
        UserCouponListResponse response = couponService.getUserCoupons(userId, null);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getCoupons()).hasSize(1);
        assertThat(response.getTotalCount()).isEqualTo(1);

        UserCouponResponse couponResponse = response.getCoupons().get(0);
        assertThat(couponResponse.getCouponId()).isEqualTo(couponId);
        assertThat(couponResponse.getCouponName()).isEqualTo("10% 할인");
        assertThat(couponResponse.getDiscountRate()).isEqualTo(10);
        assertThat(couponResponse.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 상태 필터링 (AVAILABLE)")
    void getUserCoupons_상태필터링() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon1 = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        Coupon savedCoupon1 = couponRepository.save(coupon1);
        Long couponId1 = savedCoupon1.getId();

        Coupon coupon2 = Coupon.create("C002", "20% 할인", 20, 50, now, now.plusDays(7));
        Coupon savedCoupon2 = couponRepository.save(coupon2);
        Long couponId2 = savedCoupon2.getId();

        UserCoupon userCoupon1 = UserCoupon.create(userId, couponId1, coupon1.getExpiresAt());
        UserCoupon userCoupon2 = UserCoupon.create(userId, couponId2, coupon2.getExpiresAt());
        userCoupon2.use();

        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When
        UserCouponListResponse response = couponService.getUserCoupons(userId, "AVAILABLE");

        // Then
        assertThat(response.getCoupons()).hasSize(1);
        assertThat(response.getCoupons().get(0).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("보유 쿠폰 조회 실패 - 존재하지 않는 사용자")
    void getUserCoupons_실패_존재하지않는사용자() {
        // Given
        Long invalidUserId = 99999L;

        // When & Then
        assertThatThrownBy(() -> couponService.getUserCoupons(invalidUserId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 빈 목록")
    void getUserCoupons_빈목록() {
        // Given
        User user = User.create("test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // When
        UserCouponListResponse response = couponService.getUserCoupons(userId, null);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getCoupons()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }
}
