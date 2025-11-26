package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import io.hhplus.ecommerce.infrastructure.metrics.MetricsCollector;
import io.hhplus.ecommerce.infrastructure.redis.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final MetricsCollector metricsCollector;

    /**
     * 쿠폰 발급 (선착순)
     * <p>
     * 동시성 제어: Pessimistic Lock + 분산락
     * - 기본: Pessimistic Lock (단일 인스턴스에서 정확한 수량 보장)
     * - 추가: 분산락 (여러 인스턴스 환경에서 동시성 제어)
     * - 락 키: "coupon:issue:{couponId}"
     * - 선착순 이벤트이므로 정확성이 최우선
     * <p>
     * 중복 발급 방지:
     * 1차: 애플리케이션 체크 (existsByUserIdAndCouponId)
     * 2차: DB Unique Constraint (uk_user_coupon)
     * 3차: DataIntegrityViolationException 처리
     */
    @DistributedLock(
            key = "'coupon:issue:' + #couponId",
            waitTime = 5,
            leaseTime = 10
    )
    @Transactional
    public IssueCouponResponse execute(Long couponId, IssueCouponRequest request) {
        Long userId = request.userId();
        log.info("Issuing coupon for user: {}, coupon: {}", userId, couponId);

        try {
            // 1. 사용자 검증
            userRepository.findByIdOrThrow(userId);

            // 2. 쿠폰 조회 및 발급 가능 여부 검증
            // 동시성 제어: Pessimistic Lock + 분산락
            // - 분산락: 여러 인스턴스 간 동시성 제어
            // - Pessimistic Lock: DB 레벨 동시성 제어 (추가 안전장치)
            Coupon coupon = couponRepository.findByIdWithLockOrThrow(couponId);
            coupon.validateIssuable();

            // 3. 중복 발급 방지
            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    "이미 발급받은 쿠폰입니다. userId: " + userId + ", couponId: " + couponId
                );
            }

            // 4. 쿠폰 수량 차감 (재고 체크 포함)
            coupon.issue();

            // 5. 사용자 쿠폰 생성 (DB Unique Constraint로 중복 방지)
            // 동시성 제어: DB Unique Constraint (uk_user_coupon)
            // - 7명 합의: DB Unique Constraint (100% 방어, 마지막 보루)
            // - TOCTOU 갭 제거 (애플리케이션 체크는 최적화용)
            UserCoupon userCoupon = UserCoupon.create(userId, couponId, coupon.getExpiresAt());
            try {
                userCouponRepository.save(userCoupon);
            } catch (DataIntegrityViolationException e) {
                // Unique Constraint 위반 (동시 요청으로 인한 중복 발급 시도)
                log.warn("Duplicate coupon issuance attempt blocked by DB constraint. userId: {}, couponId: {}",
                    userId, couponId);
                throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    "이미 발급받은 쿠폰입니다. (동시 요청 감지)"
                );
            }
            couponRepository.save(coupon);

            log.debug("Coupon issued successfully. userCouponId: {}, remaining quantity: {}",
                userCoupon.getId(), coupon.getRemainingQuantity());

            // 메트릭 기록: 쿠폰 발급 성공
            metricsCollector.recordCouponIssueSuccess();

            return IssueCouponResponse.of(
                userCoupon,
                coupon.getName(),
                coupon.getDiscountRate(),
                coupon.getExpiresAt(),
                coupon.getRemainingQuantity()
            );

        } catch (BusinessException e) {
            // 메트릭 기록: 쿠폰 발급 실패
            metricsCollector.recordCouponIssueFailure();
            throw e;
        } catch (Exception e) {
            // 메트릭 기록: 쿠폰 발급 실패
            metricsCollector.recordCouponIssueFailure();
            throw e;
        }
    }
}
