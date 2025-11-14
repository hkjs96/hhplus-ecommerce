package io.hhplus.ecommerce.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

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
        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
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
        assertThat(coupon.getIssuedQuantity()).isEqualTo(3);
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
        assertThat(coupon.getIssuedQuantity()).isEqualTo(100);
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

    @Test
    @DisplayName("쿠폰 생성 실패 - 할인율이 null")
    void create_할인율null_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", null, 100, now, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 할인율이 음수")
    void create_할인율음수_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", -1, 100, now, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 할인율이 100 초과")
    void create_할인율초과_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 101, 100, now, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 총 수량이 null")
    void create_총수량null_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 10, null, now, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 총 수량이 0")
    void create_총수량0_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 10, 0, now, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 총 수량이 음수")
    void create_총수량음수_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 10, -1, now, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 시작일이 null")
    void create_시작일null_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 10, 100, null, now.plusDays(7)))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 종료일이 null")
    void create_종료일null_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 10, 100, now, null))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 생성 실패 - 시작일이 종료일보다 이후")
    void create_시작일종료일역전_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When & Then
        assertThatThrownBy(() -> Coupon.create("C001", "10% 할인", 10, 100, now.plusDays(7), now))
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 검증 실패 - 만료된 쿠폰")
    void validateIssuable_만료됨_예외발생() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.create("C001", "10% 할인", 10, 100, now.minusDays(7), now.minusDays(1));

        // When & Then
        assertThatThrownBy(() -> coupon.validateIssuable())
            .isInstanceOf(io.hhplus.ecommerce.common.exception.BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", io.hhplus.ecommerce.common.exception.ErrorCode.EXPIRED_COUPON);
    }
}
