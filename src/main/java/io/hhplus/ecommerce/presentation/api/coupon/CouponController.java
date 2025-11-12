package io.hhplus.ecommerce.presentation.api.coupon;

import io.hhplus.ecommerce.application.coupon.CouponService;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponResponse;
import io.hhplus.ecommerce.application.coupon.dto.UserCouponListResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @PathVariable Long couponId,
            @Valid @RequestBody IssueCouponRequest request
    ) {
        IssueCouponResponse response = couponService.issueCoupon(couponId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/coupons")
    public ResponseEntity<UserCouponListResponse> getUserCoupons(
            @PathVariable Long userId,
            @RequestParam(required = false) String status
    ) {
        UserCouponListResponse response = couponService.getUserCoupons(userId, status);
        return ResponseEntity.ok(response);
    }
}
