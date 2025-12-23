package io.hhplus.ecommerce.application.coupon.listener;

import io.hhplus.ecommerce.application.usecase.coupon.IssueCouponActualService;
import io.hhplus.ecommerce.domain.coupon.CouponReservation;
import io.hhplus.ecommerce.domain.coupon.CouponReservationRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservedEvent;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 쿠폰 예약 완료 이벤트 리스너
 *
 * Phase 2: 실제 쿠폰 발급 처리
 * - CouponReservation DB 커밋 후 실행 (AFTER_COMMIT)
 * - Coupon.issue() + UserCoupon INSERT (ACID)
 * - 실패 시 Redis 원복 + CouponReservation.status = FAILED
 *
 * 핵심 원칙:
 * 1. TransactionPhase.AFTER_COMMIT: DB 커밋 후 실행
 * 2. 동기 처리 (NOT @Async): 실패 시 즉시 원복 필요
 * 3. 코치님 요구사항: "재고 차감 + 발급 기록은 ACID"
 *    → IssueCouponActualService.issueActual() 메서드가 하나의 @Transactional
 *
 * 흐름:
 * [예약 완료] → [DB 커밋] → [Event 발행] → [즉시 발급 처리]
 * ↓ 성공
 * [CouponReservation.status = ISSUED]
 * ↓ 실패
 * [Redis DECR (원복)] + [CouponReservation.status = FAILED]
 *
 * 설계 근거:
 * - Jay 코치 Jisu 답변: "선착순 발급과 발급 처리를 분리"
 * - "쿠폰이 발급되는 부분은 나중에 해도 됩니다" (COACH_JAY_QNA.md:192)
 * - "100번째 안에 들었다는 부분은 뒤집히지 않는 사실" (이미 CouponReservation에 기록됨)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponReservedEventListener {

    private final IssueCouponActualService issueCouponActualService;
    private final CouponReservationRepository reservationRepository;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 쿠폰 예약 완료 이벤트 처리
     *
     * 특징:
     * - TransactionPhase.AFTER_COMMIT: DB 커밋 후 실행
     * - 동기 실행 (NOT @Async): 실패 시 즉시 원복
     * - 발급 성공/실패 상태를 CouponReservation에 업데이트
     * - REQUIRES_NEW: 새 트랜잭션에서 실행 (AFTER_COMMIT이므로 이전 트랜잭션은 종료됨)
     *
     * @param event CouponReservedEvent (예약 ID, 쿠폰 ID, 사용자 ID, 순번 포함)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCouponReserved(CouponReservedEvent event) {
        log.info("Processing CouponReservedEvent: reservationId={}, couponId={}, userId={}, sequence={}",
            event.getReservationId(), event.getCouponId(), event.getUserId(), event.getSequenceNumber());

        try {
            // 실제 쿠폰 발급 (재고 차감 + 발급 기록 ACID)
            UserCoupon userCoupon = issueCouponActualService.issueActual(
                event.getCouponId(),
                event.getUserId()
            );

            // 발급 성공 - 예약 상태 업데이트 (ISSUED)
            updateReservationStatus(event.getReservationId(), true, null);

            log.info("Coupon issued successfully via event: couponId={}, userId={}, userCouponId={}",
                event.getCouponId(), event.getUserId(), userCoupon.getId());

        } catch (Exception e) {
            // 발급 실패 - Redis 원복 + 예약 상태 업데이트 (FAILED)
            log.error("Coupon issue failed, rolling back Redis: couponId={}, userId={}, error={}",
                event.getCouponId(), event.getUserId(), e.getMessage(), e);

            rollbackRedisSequence(event.getCouponId());
            updateReservationStatus(event.getReservationId(), false, e.getMessage());

            // 실패를 외부로 전파하지 않음 (이미 처리됨)
            // 사용자에게는 별도 알림 필요 (추가 구현)
        }
    }

    /**
     * Redis 순번 원복 (DECR)
     *
     * 발급 실패 시 Redis에서 차감했던 순번을 복구
     * - coupon:{couponId}:sequence--
     *
     * @param couponId 쿠폰 ID
     */
    private void rollbackRedisSequence(Long couponId) {
        try {
            String sequenceKey = String.format("coupon:%d:sequence", couponId);
            Long newSequence = redisTemplate.opsForValue().decrement(sequenceKey);

            log.warn("Redis sequence rolled back: couponId={}, newSequence={}", couponId, newSequence);

        } catch (Exception e) {
            log.error("Failed to rollback Redis sequence: couponId={}", couponId, e);
            // Redis 원복 실패는 심각한 문제 (수동 복구 필요)
            // TODO: 알림 발송 (Slack, PagerDuty 등)
        }
    }

    /**
     * 예약 상태 업데이트 (별도 트랜잭션)
     *
     * @param reservationId 예약 ID
     * @param isSuccess 성공 여부
     * @param failureReason 실패 사유 (실패 시)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateReservationStatus(Long reservationId, boolean isSuccess, String failureReason) {
        try {
            CouponReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException(
                    "CouponReservation not found: " + reservationId
                ));

            if (isSuccess) {
                reservation.markAsIssued();
                log.debug("CouponReservation marked as ISSUED: id={}", reservationId);
            } else {
                reservation.markAsFailed(failureReason);
                log.debug("CouponReservation marked as FAILED: id={}, reason={}", reservationId, failureReason);
            }

            reservationRepository.save(reservation);

        } catch (Exception e) {
            log.error("Failed to update CouponReservation status: reservationId={}, isSuccess={}",
                reservationId, isSuccess, e);
            // 상태 업데이트 실패는 심각한 문제 (수동 복구 필요)
            // TODO: 알림 발송
        }
    }
}
