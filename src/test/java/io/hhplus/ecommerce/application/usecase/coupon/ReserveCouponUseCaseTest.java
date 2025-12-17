package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.ReserveCouponResponse;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class ReserveCouponUseCaseTest {

    @Autowired
    private ReserveCouponUseCase reserveCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaCouponRepository jpaCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository jpaUserRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 테이블 초기화
        jpaCouponRepository.deleteAll();
        jpaUserRepository.deleteAll();

        // Redis 전체 Flush (테스트 간 오염 방지)
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("정상 예약 - 순번 획득 성공")
    void shouldReserveCouponSuccessfully() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-NORMAL-1",
            "정상 쿠폰",
            10,
            100,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        User user = User.create("user@test.com", "test-user");
        userRepository.save(user);

        // when
        ReserveCouponResponse response = reserveCouponUseCase.execute(coupon.getId(), user.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(coupon.getId());
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.sequenceNumber()).isEqualTo(1L); // 첫 번째 예약

        // Redis 검증
        String sequenceKey = "coupon:" + coupon.getId() + ":sequence";
        String reservationKey = "coupon:" + coupon.getId() + ":reservations";

        String sequenceValue = redisTemplate.opsForValue().get(sequenceKey);
        assertThat(sequenceValue).isEqualTo("1");

        Boolean isMember = redisTemplate.opsForSet().isMember(reservationKey, String.valueOf(user.getId()));
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("만료된 쿠폰 예약 시도 - 실패")
    void shouldFailWhenCouponExpired() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-EXPIRED-1",
            "만료된 쿠폰",
            10,
            100,
            LocalDateTime.now().minusDays(10), // 10일 전 시작
            LocalDateTime.now().minusDays(3)   // 3일 전 만료
        );
        couponRepository.save(coupon);

        User user = User.create("user@test.com", "test-user");
        userRepository.save(user);

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            reserveCouponUseCase.execute(coupon.getId(), user.getId())
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_COUPON)
            .hasMessageContaining("만료된 쿠폰입니다");

        // Redis 검증 - 예약되지 않아야 함
        String sequenceKey = "coupon:" + coupon.getId() + ":sequence";
        String reservationKey = "coupon:" + coupon.getId() + ":reservations";

        String sequenceValue = redisTemplate.opsForValue().get(sequenceKey);
        assertThat(sequenceValue).isNull(); // 순번 미증가

        Long reservationCount = redisTemplate.opsForSet().size(reservationKey);
        assertThat(reservationCount).isEqualTo(0); // 예약 기록 없음
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 예약 시도 - 실패")
    void shouldFailWhenUserNotFound() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-USER-1",
            "사용자 테스트 쿠폰",
            10,
            100,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        Long nonExistentUserId = 99999L;

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            reserveCouponUseCase.execute(coupon.getId(), nonExistentUserId)
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        // Redis 검증 - 예약되지 않아야 함
        String sequenceKey = "coupon:" + coupon.getId() + ":sequence";
        String reservationKey = "coupon:" + coupon.getId() + ":reservations";

        String sequenceValue = redisTemplate.opsForValue().get(sequenceKey);
        assertThat(sequenceValue).isNull(); // 순번 미증가

        Long reservationCount = redisTemplate.opsForSet().size(reservationKey);
        assertThat(reservationCount).isEqualTo(0); // 예약 기록 없음
    }

    @Test
    @DisplayName("수량 초과 시 예약 실패 - 선착순 마감")
    void shouldFailWhenCouponSoldOut() {
        // given
        Coupon coupon = Coupon.create(
            "COUP-SOLDOUT-1",
            "매진 테스트 쿠폰",
            10,
            5,  // totalQuantity = 5
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        // 5명의 사용자 생성
        User[] users = new User[6];
        for (int i = 0; i < 6; i++) {
            users[i] = User.create("user" + i + "@test.com", "user" + i);
            userRepository.save(users[i]);
        }

        // when - 5명 예약 성공
        for (int i = 0; i < 5; i++) {
            ReserveCouponResponse response = reserveCouponUseCase.execute(coupon.getId(), users[i].getId());
            assertThat(response.sequenceNumber()).isEqualTo((long) (i + 1));
        }

        // then - 6번째 사용자는 실패
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            reserveCouponUseCase.execute(coupon.getId(), users[5].getId())
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_SOLD_OUT)
            .hasMessageContaining("쿠폰이 모두 소진되었습니다");

        // Redis 검증
        String sequenceKey = "coupon:" + coupon.getId() + ":sequence";
        String reservationKey = "coupon:" + coupon.getId() + ":reservations";

        String sequenceValue = redisTemplate.opsForValue().get(sequenceKey);
        assertThat(sequenceValue).isEqualTo("6"); // 6번까지 INCR되었지만

        Long reservationCount = redisTemplate.opsForSet().size(reservationKey);
        assertThat(reservationCount).isEqualTo(5); // 예약은 5명만 (6번째는 rollback)
    }

    @Test
    @DisplayName("대규모 동시성 테스트 - 100명이 10개 쿠폰 예약")
    void shouldHandleLargeConcurrentReservations() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
            "COUP-CONCUR-100",
            "대규모 동시성 쿠폰",
            10,
            10,  // totalQuantity = 10
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(coupon);

        // 100명의 사용자 생성
        int totalUsers = 100;
        User[] users = new User[totalUsers];
        for (int i = 0; i < totalUsers; i++) {
            users[i] = User.create("concur" + i + "@test.com", "concur" + i);
            userRepository.save(users[i]);
        }

        // when - 100명이 동시에 예약 시도
        ExecutorService executorService = Executors.newFixedThreadPool(totalUsers);
        CountDownLatch latch = new CountDownLatch(totalUsers);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        for (int i = 0; i < totalUsers; i++) {
            int userIndex = i;
            executorService.submit(() -> {
                try {
                    ReserveCouponResponse response = reserveCouponUseCase.execute(
                        coupon.getId(),
                        users[userIndex].getId()
                    );
                    assertThat(response.sequenceNumber()).isLessThanOrEqualTo(10L);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.COUPON_SOLD_OUT) {
                        soldOutCount.incrementAndGet();
                    } else if (e.getErrorCode() == ErrorCode.ALREADY_ISSUED_COUPON) {
                        duplicateCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(10); // 정확히 10명만 성공
        assertThat(soldOutCount.get()).isEqualTo(90); // 나머지 90명은 SOLD_OUT
        assertThat(duplicateCount.get()).isEqualTo(0); // 중복 없음

        // Redis 검증
        String sequenceKey = "coupon:" + coupon.getId() + ":sequence";
        String reservationKey = "coupon:" + coupon.getId() + ":reservations";

        String sequenceValue = redisTemplate.opsForValue().get(sequenceKey);
        assertThat(Long.parseLong(sequenceValue)).isGreaterThanOrEqualTo(10L); // 최소 10까지 증가

        Long reservationCount = redisTemplate.opsForSet().size(reservationKey);
        assertThat(reservationCount).isEqualTo(10); // 예약은 정확히 10명
    }

    @Test
    @DisplayName("동일 사용자의 동시 예약은 1건만 성공하고 순번은 한 번만 증가한다")
    void duplicateReservationShouldBeRejected() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create(
            "COUP-RESERVE-1",
            "선착순 쿠폰",
            10,
            10,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(3)
        );
        couponRepository.save(coupon);

        User user = User.create("reserve@test.com", "reserve-user");
        userRepository.save(user);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateFailCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ReserveCouponResponse response = reserveCouponUseCase.execute(coupon.getId(), user.getId());
                    assertThat(response.sequenceNumber()).isGreaterThanOrEqualTo(1L);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ALREADY_ISSUED_COUPON) {
                        duplicateFailCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateFailCount.get()).isEqualTo(1);

        String sequenceKey = "coupon:" + coupon.getId() + ":sequence";
        String reservationKey = "coupon:" + coupon.getId() + ":reservations";

        String sequenceValue = redisTemplate.opsForValue().get(sequenceKey);
        assertThat(sequenceValue).isEqualTo("1");  // 순번은 한 번만 증가

        Long reservationCount = redisTemplate.opsForSet().size(reservationKey);
        assertThat(reservationCount).isEqualTo(1); // 예약자는 한 명만 기록
    }
}
