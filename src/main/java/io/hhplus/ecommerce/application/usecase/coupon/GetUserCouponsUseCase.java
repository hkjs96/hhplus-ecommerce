package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.UserCouponListResponse;
import io.hhplus.ecommerce.application.coupon.dto.UserCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.coupon.UserCouponProjection;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserCouponsUseCase {

    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    /**
     * 사용자 쿠폰 조회 (쿠폰 상세 정보 포함)
     * STEP 08 최적화:
     * - 기존: findByUserId() + N번 findById() (N+1 문제, 11 queries 예상)
     * - 개선: Native Query로 JOIN 조회 (1 query)
     * - 성능 향상: 90.9% (11 queries → 1 query)
     *
     * Note: status가 null이거나 empty인 경우, Native Query의 WHERE 조건에서
     *       (:status IS NULL OR uc.status = :status) 부분이 항상 TRUE가 되어
     *       모든 상태의 쿠폰을 조회합니다.
     */
    public UserCouponListResponse execute(Long userId, String status) {
        log.info("Getting coupons for user: {} with status: {} using optimized Native Query", userId, status);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. Native Query로 쿠폰 정보 포함 조회 (Single Query)
        // status가 null이거나 empty면 모든 상태 조회, 값이 있으면 해당 상태만 조회
        String statusParam = (status == null || status.isEmpty()) ? null : status.toUpperCase();

        List<UserCouponProjection> projectionsFromDb =
            userCouponRepository.findUserCouponsWithDetails(userId, statusParam);

        if (projectionsFromDb.isEmpty()) {
            log.debug("No coupons found for user: {} with status: {}", userId, statusParam);
            return UserCouponListResponse.of(userId, List.of());
        }

        // 3. Projection → DTO 변환
        List<UserCouponResponse> couponResponses = projectionsFromDb.stream()
            .map(proj -> new UserCouponResponse(
                proj.getUserCouponId(),
                proj.getCouponId(),
                proj.getCouponName(),
                proj.getDiscountRate(),
                proj.getStatus(),
                proj.getIssuedAt(),
                proj.getUsedAt(),
                proj.getExpiresAt()
            ))
            .toList();

        log.info("Found {} coupons for user: {} using optimized query", couponResponses.size(), userId);
        return UserCouponListResponse.of(userId, couponResponses);
    }
}
