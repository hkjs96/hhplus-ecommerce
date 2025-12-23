package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.infrastructure.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 쿠폰 발급 서비스
 *
 * Phase 2: 쿠폰 발급 (Event Listener에서 호출)
 * - Coupon.issue() (재고 차감: issuedQuantity++)
 * - UserCoupon INSERT (발급 기록)
 * - Redis SADD (중복 방지)
 *
 * ✅ 이 메서드 전체가 하나의 @Transactional (ACID 보장)
 * - 코치님 요구사항: "재고 차감과 발급 기록, 이 두 개가 액시드하게(ACID) 처리되어야 한다"
 * - COACH_QNA_SUMMARY.md:96
 *
 * 중요:
 * - 선착순 자격은 이미 CouponReservation에서 확정됨 (뒤집히지 않는 사실)
 * - 이 서비스는 "발급 처리"만 담당
 * - 실패 시 EventListener에서 Redis 원복 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponActualService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final MetricsCollector metricsCollector;

    /**
     * 실제 쿠폰 발급 (재고 차감 + 발급 기록)
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급된 UserCoupon
     * @throws BusinessException 재고 부족, 중복 발급 등
     */
    @Transactional
    public UserCoupon issueActual(Long couponId, Long userId) {
        log.debug("Starting actual coupon issue: couponId={}, userId={}", couponId, userId);

        try {
            // 1. 쿠폰 조회 및 재고 차감 (Pessimistic Lock)
            //    - Coupon.issuedQuantity++ (재고 차감)
            //    - 동시성 제어: Pessimistic Lock으로 정확성 보장
            Coupon coupon = couponRepository.findByIdWithLockOrThrow(couponId);

            // 도메인 로직: 재고 검증 + 차감
            coupon.issue();  // issuedQuantity++
            couponRepository.save(coupon);

            log.debug("Coupon stock decreased: couponId={}, issuedQuantity={}, remaining={}",
                couponId, coupon.getIssuedQuantity(), coupon.getRemainingQuantity());

            // 2. 발급 기록 저장 (UserCoupon INSERT)
            UserCoupon userCoupon = UserCoupon.create(
                userId,
                couponId,
                coupon.getExpiresAt()
            );
            userCoupon = userCouponRepository.save(userCoupon);

            log.debug("UserCoupon created: id={}, userId={}, couponId={}",
                userCoupon.getId(), userId, couponId);

            // 3. Redis 발급 기록 (중복 방지용)
            String issuedKey = buildIssuedKey(couponId);
            redisTemplate.opsForSet().add(issuedKey, String.valueOf(userId));

            log.info("Coupon issued successfully: couponId={}, userId={}, userCouponId={}",
                couponId, userId, userCoupon.getId());

            // 메트릭 기록
            metricsCollector.recordCouponIssueSuccess();

            return userCoupon;

        } catch (BusinessException e) {
            log.warn("Coupon issue failed: couponId={}, userId={}, error={}",
                couponId, userId, e.getMessage());
            metricsCollector.recordCouponIssueFailure();
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during coupon issue: couponId={}, userId={}",
                couponId, userId, e);
            metricsCollector.recordCouponIssueFailure();
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "쿠폰 발급 중 오류가 발생했습니다"
            );
        }
    }

    /**
     * Redis 발급자 Set 키 생성
     * 패턴: coupon:{couponId}:issued
     */
    private String buildIssuedKey(Long couponId) {
        return String.format("coupon:%d:issued", couponId);
    }
}
