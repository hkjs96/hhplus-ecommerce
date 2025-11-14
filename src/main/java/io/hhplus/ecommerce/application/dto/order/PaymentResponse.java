package io.hhplus.ecommerce.application.dto.order;

import java.time.LocalDateTime;

public record PaymentResponse(
    String orderId,
    Long paidAmount,
    Long remainingBalance,
    String status,
    LocalDateTime paidAt
) {
}
