package io.hhplus.ecommerce.presentation.api.coupon;

import io.hhplus.ecommerce.application.dto.coupon.*;
import io.hhplus.ecommerce.common.exception.BusinessException;
import io.hhplus.ecommerce.common.exception.ErrorCode;
import io.hhplus.ecommerce.presentation.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/coupons")
@Tag(name = "4. 쿠폰", description = "쿠폰 발급 및 조회 API")
public class CouponController {

    // Mock 데이터 저장소
    private final Map<String, CouponData> couponStore = new ConcurrentHashMap<>();
    private final Map<String, UserCouponData> userCouponStore = new ConcurrentHashMap<>();

    public CouponController() {
        // 쿠폰 초기화
        couponStore.put("C001", new CouponData(
            "C001",
            "10% 할인 쿠폰",
            10,
            100,
            new AtomicInteger(95),
            LocalDateTime.now().plusDays(30)
        ));
        couponStore.put("C002", new CouponData(
            "C002",
            "20% 할인 쿠폰",
            20,
            50,
            new AtomicInteger(48),
            LocalDateTime.now().plusDays(15)
        ));

        // 발급된 쿠폰 (테스트용)
        userCouponStore.put("UC001", new UserCouponData(
            "UC001",
            "U001",
            "C001",
            "10% 할인 쿠폰",
            10,
            "AVAILABLE",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30)
        ));
    }

    /**
     * 4.1 쿠폰 발급 (선착순)
     * POST /coupons/{couponId}/issue
     */
    @Operation(
        summary = "쿠폰 발급",
        description = "선착순 쿠폰을 발급합니다. 1인 1매 제한이 있으며, 수량이 소진되면 발급할 수 없습니다."
    )
    @PostMapping("/{couponId}/issue")
    public ApiResponse<IssueCouponResponse> issueCoupon(
        @Parameter(description = "쿠폰 ID", required = true)
        @PathVariable String couponId,
        @Valid @RequestBody IssueCouponRequest request
    ) {
        log.info("POST /coupons/{}/issue - userId: {}", couponId, request.getUserId());

        // 쿠폰 조회
        CouponData coupon = couponStore.get(couponId);
        if (coupon == null) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }

        // 만료 확인
        if (LocalDateTime.now().isAfter(coupon.expiresAt)) {
            throw new BusinessException(ErrorCode.EXPIRED_COUPON);
        }

        // 중복 발급 체크 (1인 1매)
        boolean alreadyIssued = userCouponStore.values().stream()
            .anyMatch(uc -> uc.userId.equals(request.getUserId()) && uc.couponId.equals(couponId));

        if (alreadyIssued) {
            throw new BusinessException(ErrorCode.ALREADY_ISSUED);
        }

        // 수량 확인 및 차감 (Optimistic Lock 시뮬레이션)
        int currentIssued = coupon.issuedQuantity.get();
        if (currentIssued >= coupon.totalQuantity) {
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 발급 수량 증가 (동시성 제어)
        int newIssued = coupon.issuedQuantity.incrementAndGet();
        if (newIssued > coupon.totalQuantity) {
            coupon.issuedQuantity.decrementAndGet(); // 롤백
            throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
        }

        // 사용자 쿠폰 생성
        String userCouponId = "UC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UserCouponData userCoupon = new UserCouponData(
            userCouponId,
            request.getUserId(),
            couponId,
            coupon.name,
            coupon.discountRate,
            "AVAILABLE",
            LocalDateTime.now(),
            coupon.expiresAt
        );
        userCouponStore.put(userCouponId, userCoupon);

        int remainingQuantity = coupon.totalQuantity - coupon.issuedQuantity.get();

        IssueCouponResponse response = new IssueCouponResponse(
            userCouponId,
            coupon.name,
            coupon.discountRate,
            coupon.expiresAt,
            remainingQuantity
        );

        return ApiResponse.success(response);
    }

    /**
     * 4.2 보유 쿠폰 조회
     * GET /users/{userId}/coupons
     */
    @Operation(
        summary = "보유 쿠폰 조회",
        description = "사용자가 보유한 쿠폰 목록을 조회합니다."
    )
    @GetMapping("/users/{userId}/coupons")
    public ApiResponse<UserCouponsResponse> getUserCoupons(
        @Parameter(description = "사용자 ID", required = true)
        @PathVariable String userId
    ) {
        log.info("GET /users/{}/coupons", userId);

        List<UserCouponResponse> coupons = userCouponStore.values().stream()
            .filter(uc -> uc.userId.equals(userId))
            .map(uc -> {
                // 만료 확인 및 상태 업데이트
                String status = uc.status;
                if ("AVAILABLE".equals(status) && LocalDateTime.now().isAfter(uc.expiresAt)) {
                    uc.status = "EXPIRED";
                    status = "EXPIRED";
                }

                return new UserCouponResponse(
                    uc.userCouponId,
                    uc.couponName,
                    uc.discountRate,
                    status,
                    uc.expiresAt
                );
            })
            .collect(Collectors.toList());

        UserCouponsResponse response = new UserCouponsResponse(coupons);
        return ApiResponse.success(response);
    }

    // Mock 데이터 클래스
    private static class CouponData {
        String couponId;
        String name;
        Integer discountRate;
        Integer totalQuantity;
        AtomicInteger issuedQuantity;
        LocalDateTime expiresAt;

        CouponData(String couponId, String name, Integer discountRate, Integer totalQuantity,
                   AtomicInteger issuedQuantity, LocalDateTime expiresAt) {
            this.couponId = couponId;
            this.name = name;
            this.discountRate = discountRate;
            this.totalQuantity = totalQuantity;
            this.issuedQuantity = issuedQuantity;
            this.expiresAt = expiresAt;
        }
    }

    private static class UserCouponData {
        String userCouponId;
        String userId;
        String couponId;
        String couponName;
        Integer discountRate;
        String status;
        LocalDateTime issuedAt;
        LocalDateTime expiresAt;

        UserCouponData(String userCouponId, String userId, String couponId, String couponName,
                       Integer discountRate, String status, LocalDateTime issuedAt, LocalDateTime expiresAt) {
            this.userCouponId = userCouponId;
            this.userId = userId;
            this.couponId = couponId;
            this.couponName = couponName;
            this.discountRate = discountRate;
            this.status = status;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }
}
