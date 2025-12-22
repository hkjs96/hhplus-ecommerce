package io.hhplus.ecommerce.application.coupon.listener;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservedEvent;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.infrastructure.redis.CouponIssueReservationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class CouponReservedEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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

    @Autowired
    private CouponIssueReservationStore couponIssueReservationStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        // 테이블 초기화
        jpaUserCouponRepository.deleteAll();
        jpaCouponRepository.deleteAll();

        // Redis 전체 Flush (테스트 간 오염 방지)
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("정상 이벤트 처리 - 쿠폰 발급 성공")
    void shouldHandleEventSuccessfully() throws InterruptedException {
        // given
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 트랜잭션 내에서 쿠폰 생성 및 이벤트 발행
        Long couponId = txTemplate.execute(status -> {
            Coupon coupon = Coupon.create(
                "COUP-EVENT-1",
                "이벤트 테스트 쿠폰",
                10,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
            );
            Coupon saved = couponRepository.save(coupon);

            // Redis 예약 상태 생성 (UseCase를 거치지 않고 예약만 시뮬레이션)
            couponIssueReservationStore.reserve(saved.getId(), 1L, saved.getTotalQuantity(), java.time.Duration.ofDays(1));

            // 이벤트 발행
            CouponReservedEvent event = new CouponReservedEvent(saved.getId(), 1L, 1L);
            eventPublisher.publishEvent(event);

            return saved.getId();
        });

        // @TransactionalEventListener는 AFTER_COMMIT이므로 비동기 처리 대기
        Thread.sleep(500);

        // then
        // UserCoupon 생성 확인
        boolean exists = userCouponRepository.existsByUserIdAndCouponId(1L, couponId);
        assertThat(exists).isTrue();

        // 실제 UserCoupon 조회하여 검증
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(1L);
        assertThat(userCoupons).hasSize(1);
        assertThat(userCoupons.get(0).getUserId()).isEqualTo(1L);
        assertThat(userCoupons.get(0).getCouponId()).isEqualTo(couponId);

        // Coupon 재고 차감 확인
        Coupon updatedCoupon = couponRepository.findByIdOrThrow(couponId);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);

        // Redis issued key 확인
        String issuedKey = "coupon:" + couponId + ":issued";
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedKey, "1");
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("발급 실패 시 Redis 원복 - 순번 감소, 예약자 제거")
    void shouldRollbackRedisOnFailure() throws InterruptedException {
        // given
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 트랜잭션 내에서 재고가 1인 쿠폰 생성 후 미리 1개 발급 (발급 실패 시뮬레이션)
        Long couponId = txTemplate.execute(status -> {
            Coupon coupon = Coupon.create(
                "COUP-ROLLBACK-1",
                "롤백 테스트 쿠폰",
                10,
                1,  // totalQuantity = 1 (1개만 발급 가능)
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
            );
            Coupon saved = couponRepository.save(coupon);

            // 먼저 1개 발급 (issuedQuantity = 1, 재고 소진)
            saved.issue();
            couponRepository.save(saved);

            return saved.getId();
        });

        // 새 트랜잭션: 이미 소진된 쿠폰에 대한 예약 이벤트 발행
        txTemplate.execute(status -> {
            // Redis 예약 상태 생성 (DB 재고와 불일치하게 예약이 잡혔다고 가정)
            couponIssueReservationStore.reserve(couponId, 2L, 1, java.time.Duration.ofDays(1));

            // 이벤트 발행 (발급 실패 예상: SOLD_OUT)
            CouponReservedEvent event = new CouponReservedEvent(couponId, 2L, 2L);
            eventPublisher.publishEvent(event);

            return null;
        });

        // 비동기 처리 대기
        Thread.sleep(500);

        // then
        // UserCoupon이 생성되지 않았는지 확인 (userId=2)
        boolean exists = userCouponRepository.existsByUserIdAndCouponId(2L, couponId);
        assertThat(exists).isFalse();

        // Redis 원복 확인
        String remainingKey = "coupon:" + couponId + ":remaining";
        String reservationKey = "coupon:" + couponId + ":reservation:2";
        String reservationSetKey = "coupon:" + couponId + ":reservations";

        // 재고 소진으로 실패한 경우 remaining은 복구하지 않음 (cancel)
        String remainingValue = redisTemplate.opsForValue().get(remainingKey);
        assertThat(remainingValue).isEqualTo("0");

        String reservationValue = redisTemplate.opsForValue().get(reservationKey);
        assertThat(reservationValue).isNull();

        Boolean isMember = redisTemplate.opsForSet().isMember(reservationSetKey, "2");
        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("여러 이벤트 순차 처리 - 각각 발급 성공")
    void shouldHandleMultipleEventsSequentially() throws InterruptedException {
        // given
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Long couponId = txTemplate.execute(status -> {
            Coupon coupon = Coupon.create(
                "COUP-MULTI-EVENT-1",
                "다중 이벤트 쿠폰",
                10,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
            );
            Coupon saved = couponRepository.save(coupon);

            // Redis 예약 상태 생성 (3개 예약)
            couponIssueReservationStore.reserve(saved.getId(), 1L, saved.getTotalQuantity(), java.time.Duration.ofDays(1));
            couponIssueReservationStore.reserve(saved.getId(), 2L, saved.getTotalQuantity(), java.time.Duration.ofDays(1));
            couponIssueReservationStore.reserve(saved.getId(), 3L, saved.getTotalQuantity(), java.time.Duration.ofDays(1));

            // 3개 이벤트 발행
            for (long userId = 1; userId <= 3; userId++) {
                CouponReservedEvent event = new CouponReservedEvent(saved.getId(), userId, userId);
                eventPublisher.publishEvent(event);
            }

            return saved.getId();
        });

        // 비동기 처리 대기
        Thread.sleep(1000);

        // then
        // 3개 UserCoupon 생성 확인
        boolean exists1 = userCouponRepository.existsByUserIdAndCouponId(1L, couponId);
        boolean exists2 = userCouponRepository.existsByUserIdAndCouponId(2L, couponId);
        boolean exists3 = userCouponRepository.existsByUserIdAndCouponId(3L, couponId);

        assertThat(exists1).isTrue();
        assertThat(exists2).isTrue();
        assertThat(exists3).isTrue();

        // Coupon 재고 확인
        Coupon updatedCoupon = couponRepository.findByIdOrThrow(couponId);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(3);
    }
}
