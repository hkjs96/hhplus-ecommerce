package io.hhplus.ecommerce.application.coupon;

import io.hhplus.ecommerce.application.coupon.dto.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponStatus;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    public IssueCouponResponse issueCoupon(String couponId, IssueCouponRequest request) {
        String userId = request.getUserId();

        userRepository.findByIdOrThrow(userId);
        Coupon coupon = couponRepository.findByIdOrThrow(couponId);

        coupon.validateIssuable();

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    "이미 발급받은 쿠폰입니다. userId: " + userId + ", couponId: " + couponId
            );
        }

        boolean issued = coupon.tryIssue();
        if (!issued) {
            throw new BusinessException(
                    ErrorCode.COUPON_SOLD_OUT,
                    "쿠폰이 모두 소진되었습니다. couponId: " + couponId
            );
        }

        String userCouponId = java.util.UUID.randomUUID().toString();
        UserCoupon userCoupon = UserCoupon.create(userCouponId, userId, couponId, coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);
        couponRepository.save(coupon);

        return IssueCouponResponse.of(
                userCoupon,
                coupon.getName(),
                coupon.getDiscountRate(),
                coupon.getExpiresAt(),
                coupon.getRemainingQuantity()
        );
    }

    public UserCouponListResponse getUserCoupons(String userId, String status) {
        userRepository.findByIdOrThrow(userId);

        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);

        Stream<UserCoupon> stream = userCoupons.stream();
        if (status != null && !status.isEmpty()) {
            CouponStatus couponStatus = CouponStatus.valueOf(status.toUpperCase());
            stream = stream.filter(uc -> uc.getStatus() == couponStatus);
        }

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

        return UserCouponListResponse.of(userId, couponResponses);
    }
}
