package io.hhplus.ecommerce.presentation.api.coupon;

import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponResponse;
import io.hhplus.ecommerce.application.coupon.dto.UserCouponListResponse;
import io.hhplus.ecommerce.application.usecase.coupon.GetUserCouponsUseCase;
import io.hhplus.ecommerce.application.usecase.coupon.IssueCouponUseCase;
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
    private final GetUserCouponsUseCase getUserCouponsUseCase;

    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @PathVariable Long couponId,
            @Valid @RequestBody IssueCouponRequest request
    ) {
        IssueCouponResponse response = issueCouponUseCase.execute(couponId, request);
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
