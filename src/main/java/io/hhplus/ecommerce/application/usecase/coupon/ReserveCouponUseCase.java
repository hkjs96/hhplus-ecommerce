package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.ReserveCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservedEvent;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.redis.CouponIssueReservationStore;
import io.hhplus.ecommerce.infrastructure.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 선착순 쿠폰 예약 UseCase
 *
 * Phase 1: 선착순 판정 (Redis가 Single Source of Truth)
 * - Redis INCR로 순번 획득 (원자적, 락 불필요)
 * - Redis SADD로 중복 발급 방지 (원자적)
 * - Event 발행 (AFTER_COMMIT)
 *
 * Phase 2: 쿠폰 발급 (Event Listener에서 처리)
 * - Coupon.issue() (재고 차감)
 * - UserCoupon INSERT (발급 기록)
 * - 이 두 개가 하나의 트랜잭션 (ACID)
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ReserveCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final CouponIssueReservationStore couponIssueReservationStore;
    private final ApplicationEventPublisher eventPublisher;
    private final MetricsCollector metricsCollector;

    private static final Duration COUPON_RESERVATION_TTL = Duration.ofDays(1);

    /**
     * 선착순 쿠폰 예약
     *
     * 1. 중복 예약 체크 (Redis SISMEMBER)
     * 2. Redis INCR로 순번 획득
     * 3. 수량 체크
     * 4. Redis SADD로 예약 기록 (멱등성 보장)
     * 5. Event 발행
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 예약 결과 (순번 포함)
     */
    @Transactional
    public ReserveCouponResponse execute(Long couponId, Long userId) {
        log.info("Reserving coupon for user: {}, coupon: {}", userId, couponId);

        try {
            // 1. 사용자 검증
            userRepository.findByIdOrThrow(userId);

            // 2. 쿠폰 조회 및 유효성 검증
            Coupon coupon = couponRepository.findByIdOrThrow(couponId);
            coupon.validateIssuable();

            long totalQuantity = coupon.getTotalQuantity();
            CouponIssueReservationStore.ReserveResponse reserved = couponIssueReservationStore.reserve(
                couponId,
                userId,
                totalQuantity,
                COUPON_RESERVATION_TTL
            );

            if (reserved.result() == CouponIssueReservationStore.ReserveResult.SOLD_OUT) {
                throw new BusinessException(
                    ErrorCode.COUPON_SOLD_OUT,
                    "쿠폰이 모두 소진되었습니다."
                );
            }
            if (reserved.result() == CouponIssueReservationStore.ReserveResult.ALREADY_RESERVED) {
                throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    String.format("이미 예약한 쿠폰입니다. userId: %d, couponId: %d", userId, couponId)
                );
            }
            if (reserved.result() == CouponIssueReservationStore.ReserveResult.ALREADY_ISSUED) {
                throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    String.format("이미 발급받은 쿠폰입니다. userId: %d, couponId: %d", userId, couponId)
                );
            }

            long sequence = totalQuantity - reserved.remainingAfter();

            // 7. Event 발행 (AFTER_COMMIT 시점에 발행됨)
            //    → CouponReservedEventListener에서 실제 발급 처리
            CouponReservedEvent event = new CouponReservedEvent(
                couponId,
                userId,
                sequence
            );
            eventPublisher.publishEvent(event);

            log.debug("Published CouponReservedEvent: couponId={}, userId={}", couponId, userId);

            // 메트릭 기록
            metricsCollector.recordCouponReservationSuccess();

            return ReserveCouponResponse.of(
                couponId,
                userId,
                sequence
            );

        } catch (BusinessException e) {
            log.warn("Coupon reservation failed: userId={}, couponId={}, error={}",
                userId, couponId, e.getMessage());
            metricsCollector.recordCouponReservationFailure();
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during coupon reservation: userId={}, couponId={}",
                userId, couponId, e);
            metricsCollector.recordCouponReservationFailure();
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "쿠폰 예약 중 오류가 발생했습니다"
            );
        }
    }

    /**
     * (Deprecated) Redis 키 유틸은 CouponIssueReservationStore로 이동
     */
}
