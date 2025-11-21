package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.UserCouponListResponse;
import io.hhplus.ecommerce.application.coupon.dto.UserCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
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
        log.info("Getting coupons for user: {} with status: {}", userId, status);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. Repository에서 DTO로 변환된 데이터 조회
        // 코치 피드백 반영: Projection → DTO 변환을 Repository에서 수행
        String statusParam = (status == null || status.isEmpty()) ? null : status.toUpperCase();
        List<UserCouponResponse> couponResponses =
            userCouponRepository.findUserCouponsAsDto(userId, statusParam);

        log.info("Found {} coupons for user: {}", couponResponses.size(), userId);
        return UserCouponListResponse.of(userId, couponResponses);
    }
}
