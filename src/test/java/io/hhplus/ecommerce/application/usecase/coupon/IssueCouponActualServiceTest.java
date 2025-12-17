package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponStatus;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class IssueCouponActualServiceTest {

    @Autowired
    private IssueCouponActualService issueCouponActualService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaCouponRepository jpaCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaUserCouponRepository jpaUserCouponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 테이블 초기화
        jpaUserCouponRepository.deleteAll();
        jpaCouponRepository.deleteAll();

        // Redis 전체 Flush (테스트 간 오염 방지)
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("정상 발급 - 재고 차감, UserCoupon 생성, Redis 기록")
    void shouldIssueSuccessfully() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-ISSUE-1",
            "정상 발급 쿠폰",
            10,
            10,  // totalQuantity = 10
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);
        Long userId = 1L;

        // when
        UserCoupon userCoupon = issueCouponActualService.issueActual(coupon.getId(), userId);

        // then
        assertThat(userCoupon).isNotNull();
        assertThat(userCoupon.getId()).isNotNull();
        assertThat(userCoupon.getUserId()).isEqualTo(userId);
        assertThat(userCoupon.getCouponId()).isEqualTo(coupon.getId());
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);

        // 재고 차감 확인
        Coupon updatedCoupon = couponRepository.findByIdOrThrow(coupon.getId());
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(9);

        // Redis 기록 확인
        String issuedKey = "coupon:" + coupon.getId() + ":issued";
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedKey, String.valueOf(userId));
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("재고 소진 - 쿠폰 품절 시 실패")
    void shouldFailWhenCouponSoldOut() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-SOLDOUT-1",
            "품절 쿠폰",
            10,
            5,  // totalQuantity = 5
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        // 5개 모두 발급 (재고 소진)
        for (long i = 1; i <= 5; i++) {
            issueCouponActualService.issueActual(coupon.getId(), i);
        }

        // when & then - 6번째 발급 시도는 실패
        Long userId = 6L;
        assertThatThrownBy(() ->
            issueCouponActualService.issueActual(coupon.getId(), userId)
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT)
            .hasMessageContaining("쿠폰이 모두 소진되었습니다");

        // 재고 확인
        Coupon updatedCoupon = couponRepository.findByIdOrThrow(coupon.getId());
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(5);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(0);

        // Redis 확인 - 6번째 사용자는 기록되지 않음
        String issuedKey = "coupon:" + coupon.getId() + ":issued";
        Long setSize = redisTemplate.opsForSet().size(issuedKey);
        assertThat(setSize).isEqualTo(5); // 5명만 기록됨
    }

    @Test
    @DisplayName("쿠폰 없음 - 존재하지 않는 쿠폰 ID로 발급 시도")
    void shouldFailWhenCouponNotFound() {
        // given
        Long nonExistentCouponId = 99999L;
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() ->
            issueCouponActualService.issueActual(nonExistentCouponId, userId)
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_COUPON)
            .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("Redis 추적 검증 - 발급 후 Redis Set에 userId 기록 확인")
    void shouldTrackIssuanceInRedis() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-REDIS-1",
            "Redis 추적 쿠폰",
            10,
            10,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);
        Long userId = 100L;

        // when
        issueCouponActualService.issueActual(coupon.getId(), userId);

        // then - Redis Set 확인
        String issuedKey = "coupon:" + coupon.getId() + ":issued";
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedKey, String.valueOf(userId));
        assertThat(isMember).isTrue();

        // Set 크기 확인
        Long setSize = redisTemplate.opsForSet().size(issuedKey);
        assertThat(setSize).isEqualTo(1);
    }

    @Test
    @DisplayName("순차 발급 - 여러 사용자에게 순차적으로 발급")
    void shouldIssueSequentiallyToMultipleUsers() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-MULTI-1",
            "다중 발급 쿠폰",
            10,
            10,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        // when - 3명에게 순차 발급
        UserCoupon userCoupon1 = issueCouponActualService.issueActual(coupon.getId(), 1L);
        UserCoupon userCoupon2 = issueCouponActualService.issueActual(coupon.getId(), 2L);
        UserCoupon userCoupon3 = issueCouponActualService.issueActual(coupon.getId(), 3L);

        // then - UserCoupon 생성 확인
        assertThat(userCoupon1.getUserId()).isEqualTo(1L);
        assertThat(userCoupon2.getUserId()).isEqualTo(2L);
        assertThat(userCoupon3.getUserId()).isEqualTo(3L);

        // 재고 차감 확인
        Coupon updatedCoupon = couponRepository.findByIdOrThrow(coupon.getId());
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(3);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(7);

        // Redis Set 확인
        String issuedKey = "coupon:" + coupon.getId() + ":issued";
        Long setSize = redisTemplate.opsForSet().size(issuedKey);
        assertThat(setSize).isEqualTo(3);

        // 각 userId가 Redis에 기록되었는지 확인
        Boolean isMember1 = redisTemplate.opsForSet().isMember(issuedKey, "1");
        Boolean isMember2 = redisTemplate.opsForSet().isMember(issuedKey, "2");
        Boolean isMember3 = redisTemplate.opsForSet().isMember(issuedKey, "3");
        assertThat(isMember1).isTrue();
        assertThat(isMember2).isTrue();
        assertThat(isMember3).isTrue();
    }

    @Test
    @DisplayName("남은 재고 확인 - 발급 후 remainingQuantity 정확성 검증")
    void shouldCalculateRemainingStockCorrectly() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-STOCK-1",
            "재고 확인 쿠폰",
            10,
            10,  // totalQuantity = 10
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        // when - 3개 발급
        issueCouponActualService.issueActual(coupon.getId(), 1L);
        issueCouponActualService.issueActual(coupon.getId(), 2L);
        issueCouponActualService.issueActual(coupon.getId(), 3L);

        // then
        Coupon updatedCoupon = couponRepository.findByIdOrThrow(coupon.getId());
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(3);
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(7);
        assertThat(updatedCoupon.getTotalQuantity()).isEqualTo(10);
    }
}
