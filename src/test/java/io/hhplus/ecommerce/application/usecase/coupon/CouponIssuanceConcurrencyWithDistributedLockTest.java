package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 테스트 (분산락 적용)
 *
 * 선착순 100명 쿠폰을 1000명이 동시에 발급 요청할 때
 * 분산락이 정확히 100개만 발급하는지 검증합니다.
 */
@SpringBootTest
@Import(TestContainersConfig.class)
class CouponIssuanceConcurrencyWithDistributedLockTest {

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaCouponRepository jpaCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaUserCouponRepository jpaUserCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository jpaUserRepository;

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        jpaUserCouponRepository.deleteAll();
        jpaCouponRepository.deleteAll();
        jpaUserRepository.deleteAll();

        // 테스트 쿠폰 생성 (선착순 100명)
        testCoupon = Coupon.create(
                "COUP-" + (System.currentTimeMillis() % 100000000),  // 20자 이하로 제한
                "선착순 테스트 쿠폰",
                10,
                100,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(testCoupon);

        // 테스트 사용자 200명 생성
        testUsers = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            User user = User.create("test-user-coupon-" + i + "@test.com", "test-user-coupon-" + i);
            user.charge(100000L);
            userRepository.save(user);
            testUsers.add(user);
        }
    }

    @Test
    @DisplayName("선착순 100명 쿠폰 - 200명 동시 요청 시 정확히 100개만 발급 (분산락)")
    void 분산락_쿠폰발급_동시성_테스트_선착순100명() throws InterruptedException {
        // Given
        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 200명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(index);
                    IssueCouponRequest request = new IssueCouponRequest(user.getId());
                    issueCouponUseCase.execute(testCoupon.getId(), request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 100개만 발급 성공
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);

        Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);

        // 발급된 UserCoupon 개수 확인 (모든 사용자의 쿠폰 조회)
        long issuedCount = testUsers.stream()
                .filter(user -> userCouponRepository.existsByUserIdAndCouponId(user.getId(), testCoupon.getId()))
                .count();
        assertThat(issuedCount).isEqualTo(100);
    }

    @Test
    @DisplayName("선착순 10명 쿠폰 - 100명 동시 요청 시 정확히 10개만 발급 (분산락)")
    void 분산락_쿠폰발급_동시성_테스트_선착순10명() throws InterruptedException {
        // Given: 선착순 10명 쿠폰 생성
        Coupon limitedCoupon = Coupon.create(
                "COUP10-" + (System.currentTimeMillis() % 10000000),  // 20자 이하로 제한
                "선착순 10명 쿠폰",
                20,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );
        couponRepository.save(limitedCoupon);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 100명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(index);
                    IssueCouponRequest request = new IssueCouponRequest(user.getId());
                    issueCouponUseCase.execute(limitedCoupon.getId(), request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 10개만 발급 성공
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);

        Coupon coupon = couponRepository.findById(limitedCoupon.getId()).orElseThrow();
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 사용자가 중복 발급 시도 시 1개만 발급됨 (분산락 + DB Constraint)")
    void 분산락_쿠폰발급_중복방지_테스트() throws InterruptedException {
        // Given: 동일 사용자
        User user = testUsers.get(0);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When: 같은 사용자가 10번 동시 발급 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    IssueCouponRequest request = new IssueCouponRequest(user.getId());
                    issueCouponUseCase.execute(testCoupon.getId(), request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 1개만 성공, 나머지 9개는 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // 해당 사용자의 쿠폰 개수 확인
        boolean exists = userCouponRepository.existsByUserIdAndCouponId(user.getId(), testCoupon.getId());
        assertThat(exists).isTrue();
    }
}
