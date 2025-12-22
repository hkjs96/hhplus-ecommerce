package io.hhplus.ecommerce.infrastructure.kafka.message;

import java.time.LocalDateTime;

public record CouponIssueRequestedMessage(
    Long couponId,
    Long userId,
    String requestId,
    LocalDateTime requestedAt
) {
    public static CouponIssueRequestedMessage of(Long couponId, Long userId, String requestId) {
        return new CouponIssueRequestedMessage(couponId, userId, requestId, LocalDateTime.now());
    }
}

