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

/**
 * Coupon Application Service
 * - 선착순 쿠폰 발급 유스케이스 구현 (Step 6 핵심)
 * - 동시성 제어: AtomicInteger 사용
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    /**
     * 쿠폰 발급 (선착순)
     * API: POST /coupons/{couponId}/issue
     *
     * 동시성 제어:
     * - Coupon.tryIssue()에서 AtomicInteger.compareAndSet()으로 처리
     * - 200명 요청 → 정확히 100개만 발급
     *
     * @param couponId 쿠폰 ID
     * @param request 발급 요청 (userId)
     * @return 발급된 쿠폰 정보
     */
    public IssueCouponResponse issueCoupon(String couponId, IssueCouponRequest request) {
        String userId = request.getUserId();

        // 1. 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + userId
                ));

        // 2. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_COUPON,
                        "유효하지 않은 쿠폰입니다. couponId: " + couponId
                ));

        // 3. 쿠폰 유효성 검증 (만료 여부 확인)
        coupon.validateIssuable();

        // 4. 중복 발급 체크 (1인 1매 제한)
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new BusinessException(
                    ErrorCode.ALREADY_ISSUED_COUPON,
                    "이미 발급받은 쿠폰입니다. userId: " + userId + ", couponId: " + couponId
            );
        }

        // 5. 쿠폰 발급 시도 (동시성 제어 - AtomicInteger CAS)
        boolean issued = coupon.tryIssue();
        if (!issued) {
            throw new BusinessException(
                    ErrorCode.COUPON_SOLD_OUT,
                    "쿠폰이 모두 소진되었습니다. couponId: " + couponId
            );
        }

        // 6. UserCoupon 생성
        String userCouponId = java.util.UUID.randomUUID().toString();
        UserCoupon userCoupon = UserCoupon.create(userCouponId, userId, couponId, coupon.getExpiresAt());
        userCouponRepository.save(userCoupon);

        // 7. Coupon 저장 (issuedQuantity 업데이트)
        couponRepository.save(coupon);

        // 8. 응답 반환
        return IssueCouponResponse.of(
                userCoupon,
                coupon.getName(),
                coupon.getDiscountRate(),
                coupon.getExpiresAt(),
                coupon.getRemainingQuantity()
        );
    }

    /**
     * 보유 쿠폰 조회
     * API: GET /users/{userId}/coupons?status={status}
     *
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 필터 (AVAILABLE, USED, EXPIRED) - optional
     * @return 보유 쿠폰 목록
     */
    public UserCouponListResponse getUserCoupons(String userId, String status) {
        // 1. 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다. userId: " + userId
                ));

        // 2. 사용자 쿠폰 조회
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);

        // 3. 상태 필터링
        Stream<UserCoupon> stream = userCoupons.stream();
        if (status != null && !status.isEmpty()) {
            CouponStatus couponStatus = CouponStatus.valueOf(status.toUpperCase());
            stream = stream.filter(uc -> uc.getStatus() == couponStatus);
        }

        // 4. DTO 변환
        List<UserCouponResponse> couponResponses = stream
                .map(uc -> {
                    // 쿠폰 정보 조회 (이름, 할인율, 만료일)
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.INVALID_COUPON,
                                    "유효하지 않은 쿠폰입니다. couponId: " + uc.getCouponId()
                            ));

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
