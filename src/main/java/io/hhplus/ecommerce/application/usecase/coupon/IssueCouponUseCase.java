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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    @Transactional
    public IssueCouponResponse execute(Long couponId, IssueCouponRequest request) {
        Long userId = request.userId();
        log.info("Issuing coupon for user: {}, coupon: {}", userId, couponId);

        // 1. 사용자 검증
        userRepository.findByIdOrThrow(userId);

        // 2. 쿠폰 조회 및 발급 가능 여부 검증
        Coupon coupon = couponRepository.findByIdOrThrow(couponId);
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

        // 5. 사용자 쿠폰 생성
        UserCoupon userCoupon = UserCoupon.create(userId, couponId, coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);
        couponRepository.save(coupon);

        log.debug("Coupon issued successfully. userCouponId: {}, remaining quantity: {}",
            userCoupon.getId(), coupon.getRemainingQuantity());

        return IssueCouponResponse.of(
            userCoupon,
            coupon.getName(),
            coupon.getDiscountRate(),
            coupon.getExpiresAt(),
            coupon.getRemainingQuantity()
        );
    }
}
