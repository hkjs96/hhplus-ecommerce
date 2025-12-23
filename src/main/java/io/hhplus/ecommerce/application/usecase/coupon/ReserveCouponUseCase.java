package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.ReserveCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservedEvent;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;
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

            // 3. 중복 예약 선점 (Redis SADD 결과로 원자적 제어)
            String reservationSetKey = buildReservationSetKey(couponId);
            Long added = redisTemplate.opsForSet().add(reservationSetKey, String.valueOf(userId));
            redisTemplate.expire(reservationSetKey, COUPON_RESERVATION_TTL);

            if (added == null) {
                throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Redis 예약 기록 실패"
                );
            }

            if (added == 0L) {
                throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    String.format("이미 예약한 쿠폰입니다. userId: %d, couponId: %d", userId, couponId)
                );
            }

            // 4. Redis INCR로 순번 획득 (원자적, 락 불필요)
            String sequenceKey = buildSequenceKey(couponId);
            log.info("[REDIS INCR] Calling increment for key: {}", sequenceKey);

            Long sequence = redisTemplate.opsForValue().increment(sequenceKey);
            log.info("[REDIS INCR] Result: sequence={}, couponId={}, userId={}", sequence, couponId, userId);

            if (sequence == null) {
                log.error("[REDIS INCR] Sequence is NULL for couponId: {}", couponId);
                throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Redis 순번 획득 실패"
                );
            }

            log.debug("Acquired sequence: {} for coupon: {}", sequence, couponId);

            // 5. 수량 체크 (선착순 마감 검증)
            log.info("[QUANTITY CHECK] sequence={}, totalQuantity={}, couponId={}",
                sequence, coupon.getTotalQuantity(), couponId);

            if (sequence > coupon.getTotalQuantity()) {
                log.warn("[SOLD OUT] Coupon sold out: couponId={}, sequence={}, totalQuantity={}",
                    couponId, sequence, coupon.getTotalQuantity());

                // 예약 선점 해제 (중복 차단 Set 복구)
                redisTemplate.opsForSet().remove(reservationSetKey, String.valueOf(userId));
                // 순번은 증가했지만 발급은 안되므로, 보상 트랜잭션으로 순번을 다시 감소시킬 수 있음 (선택적)
                // redisTemplate.opsForValue().decrement(sequenceKey);

                throw new BusinessException(
                    ErrorCode.COUPON_SOLD_OUT,
                    String.format("쿠폰이 모두 소진되었습니다. (%d/%d)", sequence, coupon.getTotalQuantity())
                );
            }

            log.info("[QUANTITY CHECK PASSED] sequence={} <= totalQuantity={}",
                sequence, coupon.getTotalQuantity());

            // 6. Redis Set에 예약 기록 (멱등성 보장)
            redisTemplate.expire(sequenceKey, COUPON_RESERVATION_TTL);

            log.info("Coupon reserved in Redis: userId={}, couponId={}, sequence={}",
                userId, couponId, sequence);

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
     * Redis 순번 키 생성
     * 패턴: coupon:{couponId}:sequence
     */
    private String buildSequenceKey(Long couponId) {
        return String.format("coupon:%d:sequence", couponId);
    }

    /**
     * Redis 예약자 Set 키 생성
     * 패턴: coupon:{couponId}:reservations
     */
    private String buildReservationSetKey(Long couponId) {
        return String.format("coupon:%d:reservations", couponId);
    }
}
