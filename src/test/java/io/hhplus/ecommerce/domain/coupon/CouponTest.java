package io.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Coupon Entity 단위 테스트
 * Week 3 Step 6: 핵심 비즈니스 로직 테스트 (선착순 쿠폰 발급, 동시성 제어)
 */
class CouponTest {

    @Test
    @DisplayName("쿠폰 발급 성공")
    void tryIssue_성공() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));

        // When
        boolean result = coupon.tryIssue();

        // Then
        assertThat(result).isTrue();
        assertThat(coupon.getIssuedQuantityValue()).isEqualTo(1);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 수량 소진")
    void tryIssue_수량소진_실패() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 3, now, now.plusDays(7));

        // When - 3개 모두 발급
        coupon.tryIssue();
        coupon.tryIssue();
        coupon.tryIssue();

        // Then - 4번째는 실패
        boolean result = coupon.tryIssue();
        assertThat(result).isFalse();
        assertThat(coupon.getIssuedQuantityValue()).isEqualTo(3);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("쿠폰 발급 동시성 테스트 - 200명 요청, 100개만 발급")
    void tryIssue_동시성_테스트() throws InterruptedException {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now, now.plusDays(7));

        int threadCount = 200;  // 200명이 동시에 요청
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When - 200개 스레드가 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    boolean result = coupon.tryIssue();
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();  // 모든 스레드 완료 대기
        executorService.shutdown();

        // Then - 정확히 100개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(100);
        assertThat(coupon.getIssuedQuantityValue()).isEqualTo(100);
        assertThat(coupon.getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("쿠폰 유효기간 확인 - 유효함")
    void isValid_유효함() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now.minusDays(1), now.plusDays(7));

        // When & Then
        assertThat(coupon.isValid(now)).isTrue();
    }

    @Test
    @DisplayName("쿠폰 유효기간 확인 - 아직 시작 안 됨")
    void isValid_시작전() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now.plusDays(1), now.plusDays(7));

        // When & Then
        assertThat(coupon.isValid(now)).isFalse();
    }

    @Test
    @DisplayName("쿠폰 유효기간 확인 - 만료됨")
    void isValid_만료됨() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now.minusDays(7), now.minusDays(1));

        // When & Then
        assertThat(coupon.isValid(now)).isFalse();
        assertThat(coupon.isExpired(now)).isTrue();
    }
}
