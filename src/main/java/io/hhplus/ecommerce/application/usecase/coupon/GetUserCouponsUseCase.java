package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.application.coupon.dto.UserCouponListResponse;
import io.hhplus.ecommerce.application.coupon.dto.UserCouponResponse;
import io.hhplus.ecommerce.application.usecase.UseCase;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponStatus;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserCouponsUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    public UserCouponListResponse execute(Long userId, String status) {
        log.info("Getting coupons for user: {}, status: {}", userId, status);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. 사용자 쿠폰 조회
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);

        // 3. 상태 필터링 (선택적)
        Stream<UserCoupon> stream = userCoupons.stream();
        if (status != null && !status.isEmpty()) {
            CouponStatus couponStatus = CouponStatus.valueOf(status.toUpperCase());
            stream = stream.filter(uc -> uc.getStatus() == couponStatus);
        }

        // 4. 쿠폰 정보와 함께 응답 생성
        List<UserCouponResponse> couponResponses = stream
            .map(uc -> {
                Coupon coupon = couponRepository.findByIdOrThrow(uc.getCouponId());

                return UserCouponResponse.of(
                    uc,
                    coupon.getName(),
                    coupon.getDiscountRate(),
                    coupon.getExpiresAt()
                );
            })
            .toList();

        log.debug("Found {} coupons for user: {}", couponResponses.size(), userId);
        return UserCouponListResponse.of(userId, couponResponses);
    }
}
