package io.hhplus.ecommerce.presentation.api.coupon;

import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponResponse;
import io.hhplus.ecommerce.application.coupon.dto.ReserveCouponRequest;
import io.hhplus.ecommerce.application.coupon.dto.ReserveCouponResponse;
import io.hhplus.ecommerce.application.coupon.dto.UserCouponListResponse;
import io.hhplus.ecommerce.application.usecase.coupon.GetUserCouponsUseCase;
import io.hhplus.ecommerce.application.usecase.coupon.IssueCouponUseCase;
import io.hhplus.ecommerce.application.usecase.coupon.ReserveCouponUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CouponController {

    private final IssueCouponUseCase issueCouponUseCase;
    private final ReserveCouponUseCase reserveCouponUseCase;
    private final GetUserCouponsUseCase getUserCouponsUseCase;

    /**
     * 쿠폰 발급 (기존 DB 기반)
     *
     * POST /api/coupons/{couponId}/issue
     * - Pessimistic Lock + 분산락
     */
    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @PathVariable Long couponId,
            @Valid @RequestBody IssueCouponRequest request
    ) {
        IssueCouponResponse response = issueCouponUseCase.execute(couponId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 선착순 쿠폰 예약 (Redis INCR 기반)
     *
     * POST /api/coupons/{couponId}/reserve
     * - Redis INCR로 순번 획득
     * - DB에 예약 기록 (CouponReservation)
     * - Event 발행 → 실제 발급 처리 (비동기)
     *
     * 응답:
     * {
     *   "reservationId": 123,
     *   "couponId": 1,
     *   "userId": 456,
     *   "sequenceNumber": 42,
     *   "status": "RESERVED",
     *   "message": "쿠폰 발급 예약이 완료되었습니다. (42번째)",
     *   "reservedAt": "2025-12-04T10:00:00"
     * }
     */
    @PostMapping("/coupons/{couponId}/reserve")
    public ResponseEntity<ReserveCouponResponse> reserveCoupon(
            @PathVariable Long couponId,
            @Valid @RequestBody ReserveCouponRequest request
    ) {
        ReserveCouponResponse response = reserveCouponUseCase.execute(couponId, request.userId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/coupons")
    public ResponseEntity<UserCouponListResponse> getUserCoupons(
            @PathVariable Long userId,
            @RequestParam(required = false) String status
    ) {
        UserCouponListResponse response = getUserCouponsUseCase.execute(userId, status);
        return ResponseEntity.ok(response);
    }
}
