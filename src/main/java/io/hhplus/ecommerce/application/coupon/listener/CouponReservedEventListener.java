package io.hhplus.ecommerce.application.coupon.listener;

import io.hhplus.ecommerce.application.usecase.coupon.IssueCouponActualService;
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
 * - 예약 트랜잭션 커밋 후 실행 (AFTER_COMMIT)
 * - Coupon.issue() + UserCoupon INSERT (ACID)
 * - 실패 시 Redis 원복 (순번 감소, 예약자 Set에서 제거)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponReservedEventListener {

    private final IssueCouponActualService issueCouponActualService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 쿠폰 예약 완료 이벤트 처리
     *
     * @param event CouponReservedEvent (쿠폰 ID, 사용자 ID, 순번 포함)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCouponReserved(CouponReservedEvent event) {
        log.info("Processing CouponReservedEvent: couponId={}, userId={}, sequence={}",
            event.getCouponId(), event.getUserId(), event.getSequenceNumber());

        try {
            // 실제 쿠폰 발급 (재고 차감 + 발급 기록 ACID)
            UserCoupon userCoupon = issueCouponActualService.issueActual(
                event.getCouponId(),
                event.getUserId()
            );

            log.info("Coupon issued successfully via event: couponId={}, userId={}, userCouponId={}",
                event.getCouponId(), event.getUserId(), userCoupon.getId());

        } catch (Exception e) {
            // 발급 실패 - Redis 원복
            log.error("Coupon issue failed, rolling back Redis: couponId={}, userId={}, error={}",
                event.getCouponId(), event.getUserId(), e.getMessage(), e);

            rollbackRedisState(event.getCouponId(), event.getUserId());

            // 실패를 외부로 전파하지 않음 (이미 처리됨)
        }
    }

    /**
     * Redis 상태 원복 (순번 DECR, 예약자 Set에서 제거)
     *
     * 발급 실패 시 Redis에서 처리했던 내용을 복구
     * - coupon:{couponId}:sequence--
     * - coupon:{couponId}:reservations 에서 userId 제거
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    private void rollbackRedisState(Long couponId, Long userId) {
        try {
            // 1. 순번 감소
            String sequenceKey = String.format("coupon:%d:sequence", couponId);
            Long newSequence = redisTemplate.opsForValue().decrement(sequenceKey);

            // 2. 예약자 Set에서 제거
            String reservationSetKey = String.format("coupon:%d:reservations", couponId);
            redisTemplate.opsForSet().remove(reservationSetKey, String.valueOf(userId));

            log.warn("Redis state rolled back: couponId={}, userId={}, newSequence={}", couponId, userId, newSequence);

        } catch (Exception e) {
            log.error("Failed to rollback Redis state: couponId={}, userId={}", couponId, userId, e);
            // Redis 원복 실패는 심각한 문제 (수동 복구 필요)
            // TODO: 알림 발송 (Slack, PagerDuty 등)
        }
    }
}
