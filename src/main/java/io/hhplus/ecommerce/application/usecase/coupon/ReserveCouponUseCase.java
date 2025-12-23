package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.ReserveCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservation;
import io.hhplus.ecommerce.domain.coupon.CouponReservationRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservedEvent;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 쿠폰 예약 UseCase
 *
 * Phase 1: 선착순 판정 (뒤집히지 않는 사실 확정)
 * - Redis INCR로 순번 획득 (원자적, 락 불필요)
 * - DB에 예약 기록 (CouponReservation)
 * - Event 발행 (AFTER_COMMIT)
 *
 * Phase 2: 쿠폰 발급 (Event Listener에서 처리)
 * - Coupon.issue() (재고 차감)
 * - UserCoupon INSERT (발급 기록)
 * - 이 두 개가 하나의 트랜잭션 (ACID)
 *
 * 설계 근거:
 * - Jay 코치 Jisu 답변: "선착순 판정과 발급 처리를 분리"
 * - "100번째 안에 들었다는 부분은 뒤집히지 않는 사실"
 * - Kim Jonghyeop 코치: "재고 차감 + 발급 기록은 ACID" → Event Handler에서 보장
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class ReserveCouponUseCase {

    private final CouponRepository couponRepository;
    private final CouponReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final MetricsCollector metricsCollector;

    /**
     * 선착순 쿠폰 예약
     *
     * 1. 중복 예약 체크 (DB)
     * 2. Redis INCR로 순번 획득 (원자적)
     * 3. 수량 체크
     * 4. DB에 예약 기록
     * 5. Event 발행 (AFTER_COMMIT)
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

            // 3. 중복 예약 체크 (DB)
            if (reservationRepository.existsByUserIdAndCouponId(userId, couponId)) {
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

                throw new BusinessException(
                    ErrorCode.COUPON_SOLD_OUT,
                    String.format("쿠폰이 모두 소진되었습니다. (%d/%d)", sequence, coupon.getTotalQuantity())
                );
            }

            log.info("[QUANTITY CHECK PASSED] sequence={} <= totalQuantity={}",
                sequence, coupon.getTotalQuantity());

            // 6. DB에 예약 기록 (뒤집히지 않는 사실 확정)
            CouponReservation reservation = CouponReservation.create(userId, couponId, sequence);
            reservation = reservationRepository.save(reservation);

            log.info("Coupon reserved: reservationId={}, userId={}, couponId={}, sequence={}",
                reservation.getId(), userId, couponId, sequence);

            // 7. Event 발행 (AFTER_COMMIT 시점에 발행됨)
            //    → CouponReservedEventListener에서 실제 발급 처리
            CouponReservedEvent event = new CouponReservedEvent(
                reservation.getId(),
                couponId,
                userId,
                sequence
            );
            eventPublisher.publishEvent(event);

            log.debug("Published CouponReservedEvent: reservationId={}", reservation.getId());

            // 메트릭 기록
            metricsCollector.recordCouponReservationSuccess();

            return ReserveCouponResponse.of(
                reservation.getId(),
                couponId,
                userId,
                sequence,
                reservation.getReservedAt()
            );

        } catch (BusinessException e) {
            log.warn("Coupon reservation failed: userId={}, couponId={}, error={}",
                userId, couponId, e.getMessage());
            metricsCollector.recordCouponReservationFailure();
            throw e;

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Unique Constraint 위반 = 중복 예약 시도
            if (e.getMessage() != null && e.getMessage().contains("uk_user_coupon_reservation")) {
                log.warn("Duplicate coupon reservation detected: userId={}, couponId={}", userId, couponId);
                metricsCollector.recordCouponReservationFailure();
                throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    String.format("이미 예약한 쿠폰입니다. userId: %d, couponId: %d", userId, couponId)
                );
            }
            log.error("Data integrity violation during coupon reservation: userId={}, couponId={}",
                userId, couponId, e);
            metricsCollector.recordCouponReservationFailure();
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "쿠폰 예약 중 오류가 발생했습니다"
            );

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
}
