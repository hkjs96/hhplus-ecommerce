package io.hhplus.ecommerce.application.coupon;

import io.hhplus.ecommerce.application.coupon.dto.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.*;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CouponService 단위 테스트
 * - Mock Repository 사용
 * - 선착순 쿠폰 발급 비즈니스 로직 검증 (Step 6 핵심)
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CouponService couponService;

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_성공() {
        // Given
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 100, now, now.plusDays(7));
        IssueCouponRequest request = new IssueCouponRequest(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
                .thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
                .thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(couponRepository.save(any(Coupon.class)))
                .thenReturn(coupon);

        // When
        IssueCouponResponse response = couponService.issueCoupon(couponId, request);

        // Then
        assertThat(response.getCouponId()).isEqualTo(couponId);
        assertThat(response.getCouponName()).isEqualTo("10% 할인");
        assertThat(response.getDiscountRate()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo("AVAILABLE");
        assertThat(response.getRemainingQuantity()).isEqualTo(99);  // 100 - 1

        // 쿠폰 수량 감소 확인
        assertThat(coupon.getIssuedQuantityValue()).isEqualTo(1);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(userCouponRepository).existsByUserIdAndCouponId(userId, couponId);
        verify(userCouponRepository).save(any(UserCoupon.class));
        verify(couponRepository).save(coupon);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 사용자")
    void issueCoupon_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";
        String couponId = "C001";
        IssueCouponRequest request = new IssueCouponRequest(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findById(userId);
        verify(couponRepository, never()).findById(any());
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 쿠폰")
    void issueCoupon_실패_존재하지않는쿠폰() {
        // Given
        String userId = "U001";
        String couponId = "INVALID";
        User user = User.create(userId, "test@example.com", "김항해");
        IssueCouponRequest request = new IssueCouponRequest(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COUPON);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 만료된 쿠폰")
    void issueCoupon_실패_만료된쿠폰() {
        // Given
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        // 이미 만료된 쿠폰
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 100, now.minusDays(10), now.minusDays(1));
        IssueCouponRequest request = new IssueCouponRequest(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
                .thenReturn(Optional.of(coupon));

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_COUPON);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(userCouponRepository, never()).existsByUserIdAndCouponId(any(), any());
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 중복 발급 (1인 1매 제한)")
    void issueCoupon_실패_중복발급() {
        // Given
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 100, now, now.plusDays(7));
        IssueCouponRequest request = new IssueCouponRequest(userId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
                .thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
                .thenReturn(true);  // 이미 발급받음

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_ISSUED_COUPON);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(userCouponRepository).existsByUserIdAndCouponId(userId, couponId);
        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 수량 소진")
    void issueCoupon_실패_수량소진() {
        // Given
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 1, now, now.plusDays(7));  // 총 1개
        IssueCouponRequest request = new IssueCouponRequest(userId);

        // 이미 1개 발급됨 (수량 소진)
        coupon.tryIssue();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
                .thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
                .thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(userCouponRepository).existsByUserIdAndCouponId(userId, couponId);
        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("보유 쿠폰 조회 성공")
    void getUserCoupons_성공() {
        // Given
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 100, now, now.plusDays(7));
        UserCoupon userCoupon = UserCoupon.create("UC001", userId, couponId, coupon.getExpiresAt());

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userCouponRepository.findByUserId(userId))
                .thenReturn(List.of(userCoupon));
        when(couponRepository.findById(couponId))
                .thenReturn(Optional.of(coupon));

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

        verify(userRepository).findById(userId);
        verify(userCouponRepository).findByUserId(userId);
        verify(couponRepository).findById(couponId);
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 상태 필터링 (AVAILABLE)")
    void getUserCoupons_상태필터링() {
        // Given
        String userId = "U001";
        LocalDateTime now = LocalDateTime.now();
        User user = User.create(userId, "test@example.com", "김항해");

        Coupon coupon1 = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        Coupon coupon2 = Coupon.create("C002", "20% 할인", 20, 50, now, now.plusDays(7));

        UserCoupon userCoupon1 = UserCoupon.create("UC001", userId, "C001", coupon1.getExpiresAt());
        UserCoupon userCoupon2 = UserCoupon.create("UC002", userId, "C002", coupon2.getExpiresAt());
        userCoupon2.use();  // 사용됨

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userCouponRepository.findByUserId(userId))
                .thenReturn(List.of(userCoupon1, userCoupon2));
        when(couponRepository.findById("C001"))
                .thenReturn(Optional.of(coupon1));

        // When - AVAILABLE 상태만 조회
        UserCouponListResponse response = couponService.getUserCoupons(userId, "AVAILABLE");

        // Then
        assertThat(response.getCoupons()).hasSize(1);
        assertThat(response.getCoupons().get(0).getStatus()).isEqualTo("AVAILABLE");

        verify(userRepository).findById(userId);
        verify(userCouponRepository).findByUserId(userId);
        verify(couponRepository).findById("C001");
        verify(couponRepository, never()).findById("C002");  // USED는 필터링됨
    }

    @Test
    @DisplayName("보유 쿠폰 조회 실패 - 존재하지 않는 사용자")
    void getUserCoupons_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> couponService.getUserCoupons(userId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findById(userId);
        verify(userCouponRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 빈 목록")
    void getUserCoupons_빈목록() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(userCouponRepository.findByUserId(userId))
                .thenReturn(List.of());

        // When
        UserCouponListResponse response = couponService.getUserCoupons(userId, null);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getCoupons()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);

        verify(userRepository).findById(userId);
        verify(userCouponRepository).findByUserId(userId);
    }
}
