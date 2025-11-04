package io.hhplus.ecommerce.domain.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 쿠폰 마스터 엔티티 (Rich Domain Model)
 * Week 3: Pure Java Entity (JPA 어노테이션 없음)
 *
 * 비즈니스 규칙:
 * - 선착순 쿠폰 발급 (Step 6)
 * - issuedQuantity <= totalQuantity
 * - discountRate는 0~100 사이
 * - 동시성 제어: AtomicInteger 사용 (CAS)
 */
@Getter
public class Coupon {

    private String id;
    private String name;
    private Integer discountRate;      // 할인율 (%)
    private Integer totalQuantity;     // 총 수량
    private AtomicInteger issuedQuantity;  // 발급된 수량 (AtomicInteger로 동시성 제어)
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /**
     * 생성자 (Lombok @AllArgsConstructor 대신 직접 작성)
     * AtomicInteger 필드 때문에 직접 작성
     */
    public Coupon(String id, String name, Integer discountRate, Integer totalQuantity, Integer issuedQuantity, LocalDateTime startDate, LocalDateTime endDate) {
        this.id = id;
        this.name = name;
        this.discountRate = discountRate;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = new AtomicInteger(issuedQuantity);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 쿠폰 생성 (Factory Method)
     */
    public static Coupon create(String id, String name, Integer discountRate, Integer totalQuantity, LocalDateTime startDate, LocalDateTime endDate) {
        validateDiscountRate(discountRate);
        validateTotalQuantity(totalQuantity);
        validateDateRange(startDate, endDate);

        return new Coupon(id, name, discountRate, totalQuantity, 0, startDate, endDate);
    }

    /**
     * 쿠폰 발급 시도 (동시성 제어 - CAS 기반)
     * Step 6: AtomicInteger의 compareAndSet으로 Race Condition 방지
     *
     * @return 발급 성공 여부
     */
    public boolean tryIssue() {
        while (true) {
            int current = issuedQuantity.get();

            // 수량 초과 체크
            if (current >= totalQuantity) {
                return false;  // 발급 실패 (수량 소진)
            }

            // CAS (Compare-And-Swap) 연산으로 증가 시도
            if (issuedQuantity.compareAndSet(current, current + 1)) {
                return true;  // 발급 성공
            }
            // CAS 실패 시 재시도 (다른 스레드가 먼저 변경함)
        }
    }

    /**
     * 쿠폰 유효기간 확인
     */
    public boolean isValid(LocalDateTime now) {
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    /**
     * 쿠폰 만료 여부 확인
     */
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(endDate);
    }

    /**
     * 남은 수량 확인
     */
    public int getRemainingQuantity() {
        return totalQuantity - issuedQuantity.get();
    }

    /**
     * 발급된 수량 조회 (int 타입으로 반환)
     */
    public int getIssuedQuantityValue() {
        return issuedQuantity.get();
    }

    // ====================================
    // Validation Methods
    // ====================================

    private static void validateDiscountRate(Integer discountRate) {
        if (discountRate == null || discountRate < 0 || discountRate > 100) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "할인율은 0~100 사이여야 합니다"
            );
        }
    }

    private static void validateTotalQuantity(Integer totalQuantity) {
        if (totalQuantity == null || totalQuantity <= 0) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 수량은 1 이상이어야 합니다"
            );
        }
    }

    private static void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "쿠폰 유효기간은 필수입니다"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT,
                "시작일은 종료일보다 이전이어야 합니다"
            );
        }
    }
}
