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
