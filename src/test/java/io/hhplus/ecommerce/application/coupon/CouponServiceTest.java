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

/**
 * CouponService 단위 테스트
 * - 실제 InMemory Repository 사용 (Week 3 권장 방식)
 * - 선착순 쿠폰 발급 비즈니스 로직 검증 (Step 6 핵심)
 */
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
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 100, now, now.plusDays(7));
        IssueCouponRequest request = new IssueCouponRequest(userId);

        // 실제 Repository에 데이터 저장
        userRepository.save(user);
        couponRepository.save(coupon);

        // When
        IssueCouponResponse response = couponService.issueCoupon(couponId, request);

        // Then
        assertThat(response.getCouponId()).isEqualTo(couponId);
        assertThat(response.getCouponName()).isEqualTo("10% 할인");
        assertThat(response.getDiscountRate()).isEqualTo(10);
        assertThat(response.getStatus()).isEqualTo("AVAILABLE");
        assertThat(response.getRemainingQuantity()).isEqualTo(99);  // 100 - 1

        // 쿠폰 수량 감소 확인 (Repository에서 다시 조회)
        Coupon savedCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(savedCoupon.getIssuedQuantityValue()).isEqualTo(1);

        // UserCoupon이 실제로 저장되었는지 확인
        assertThat(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).isTrue();
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 사용자")
    void issueCoupon_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";
        String couponId = "C001";
        IssueCouponRequest request = new IssueCouponRequest(userId);

        // 사용자를 저장하지 않음 (존재하지 않는 상태)

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 존재하지 않는 쿠폰")
    void issueCoupon_실패_존재하지않는쿠폰() {
        // Given
        String userId = "U001";
        String couponId = "INVALID";
        User user = User.create(userId, "test@example.com", "김항해");
        IssueCouponRequest request = new IssueCouponRequest(userId);

        // 사용자만 저장, 쿠폰은 저장하지 않음
        userRepository.save(user);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COUPON);
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

        // 실제 Repository에 데이터 저장
        userRepository.save(user);
        couponRepository.save(coupon);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_COUPON);
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

        // 실제 Repository에 데이터 저장
        userRepository.save(user);
        couponRepository.save(coupon);

        // 이미 발급받은 상태 만들기
        String userCouponId = "UC001";
        UserCoupon userCoupon = UserCoupon.create(userCouponId, userId, couponId, coupon.getExpiresAt());
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
        String userId = "U001";
        String couponId = "C001";
        User user = User.create(userId, "test@example.com", "김항해");
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create(couponId, "10% 할인", 10, 1, now, now.plusDays(7));  // 총 1개
        IssueCouponRequest request = new IssueCouponRequest(userId);

        // 이미 1개 발급됨 (수량 소진)
        coupon.tryIssue();

        // 실제 Repository에 데이터 저장
        userRepository.save(user);
        couponRepository.save(coupon);

        // When & Then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT);
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

        // 실제 Repository에 데이터 저장
        userRepository.save(user);
        couponRepository.save(coupon);
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
        String userId = "U001";
        LocalDateTime now = LocalDateTime.now();
        User user = User.create(userId, "test@example.com", "김항해");

        Coupon coupon1 = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));
        Coupon coupon2 = Coupon.create("C002", "20% 할인", 20, 50, now, now.plusDays(7));

        UserCoupon userCoupon1 = UserCoupon.create("UC001", userId, "C001", coupon1.getExpiresAt());
        UserCoupon userCoupon2 = UserCoupon.create("UC002", userId, "C002", coupon2.getExpiresAt());
        userCoupon2.use();  // 사용됨

        // 실제 Repository에 데이터 저장
        userRepository.save(user);
        couponRepository.save(coupon1);
        couponRepository.save(coupon2);
        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When - AVAILABLE 상태만 조회
        UserCouponListResponse response = couponService.getUserCoupons(userId, "AVAILABLE");

        // Then
        assertThat(response.getCoupons()).hasSize(1);
        assertThat(response.getCoupons().get(0).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("보유 쿠폰 조회 실패 - 존재하지 않는 사용자")
    void getUserCoupons_실패_존재하지않는사용자() {
        // Given
        String userId = "INVALID";

        // 사용자를 저장하지 않음 (존재하지 않는 상태)

        // When & Then
        assertThatThrownBy(() -> couponService.getUserCoupons(userId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 빈 목록")
    void getUserCoupons_빈목록() {
        // Given
        String userId = "U001";
        User user = User.create(userId, "test@example.com", "김항해");

        // 실제 Repository에 사용자만 저장, 쿠폰은 저장하지 않음
        userRepository.save(user);

        // When
        UserCouponListResponse response = couponService.getUserCoupons(userId, null);

        // Then
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getCoupons()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }
}
