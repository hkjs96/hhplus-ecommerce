package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.config.TestContainersConfig;
import org.springframework.context.annotation.Import;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestContainersConfig.class)
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class IssueCouponConcurrencyTest {

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("쿠폰 중복 발급 방지 - DB Unique Constraint로 TOCTOU 차단")
    void testDuplicateCouponIssuance_UniqueConstraint() throws InterruptedException {
        // Given: 사용자와 쿠폰 생성
        User user = User.create("test@example.com", "테스트");
        userRepository.save(user);

        Coupon coupon = Coupon.create(
            "COUPON-001",
            "테스트 쿠폰",
            10,
            100,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30)
        );
        couponRepository.save(coupon);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateFailureCount = new AtomicInteger(0);

        // When: 동일 사용자가 동일 쿠폰을 10번 동시 요청
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    IssueCouponRequest request = new IssueCouponRequest(user.getId());
                    issueCouponUseCase.execute(coupon.getId(), request);
                    successCount.incrementAndGet();
                    System.out.println("발급 성공");
                } catch (BusinessException e) {
                    if (e.getMessage().contains("이미 발급받은 쿠폰")) {
                        duplicateFailureCount.incrementAndGet();
                        System.out.println("중복 발급 차단: " + e.getMessage());
                    } else {
                        System.out.println("기타 에러: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("예외: " + e.getClass().getSimpleName());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 1개만 성공, 나머지는 중복 발급 차단
        System.out.println("성공: " + successCount.get());
        System.out.println("중복 차단: " + duplicateFailureCount.get());

        assertThat(successCount.get()).isEqualTo(1); // 1개만 성공
        assertThat(duplicateFailureCount.get()).isGreaterThan(0); // 나머지는 차단
    }

    @Test
    @DisplayName("쿠폰 재고 소진 동시성 테스트 - Pessimistic Lock")
    void testCouponStockExhaustion_PessimisticLock() throws InterruptedException {
        // Given: 재고 5개 쿠폰
        Coupon coupon = Coupon.create(
            "COUPON-002",
            "한정 쿠폰",
            10,
            5,  // 총 5개
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30)
        );
        couponRepository.save(coupon);

        // 5명의 사용자 생성
        User[] users = new User[10];
        for (int i = 0; i < 10; i++) {
            users[i] = User.create("user" + i + "@example.com", "사용자" + i);
            userRepository.save(users[i]);
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);

        // When: 10명이 동시에 쿠폰 발급 요청 (재고 5개)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    IssueCouponRequest request = new IssueCouponRequest(users[index].getId());
                    issueCouponUseCase.execute(coupon.getId(), request);
                    successCount.incrementAndGet();
                    System.out.println("발급 성공 (사용자" + index + ")");
                } catch (BusinessException e) {
                    if (e.getMessage().contains("소진")) {
                        soldOutCount.incrementAndGet();
                        System.out.println("품절: " + e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 정확히 5개만 발급, 나머지는 품절
        System.out.println("성공: " + successCount.get());
        System.out.println("품절: " + soldOutCount.get());

        assertThat(successCount.get()).isEqualTo(5); // 정확히 5개만 발급
        assertThat(soldOutCount.get()).isEqualTo(5); // 나머지 5개는 품절
    }
}
