package io.hhplus.ecommerce.application.order.dto;

import java.time.LocalDateTime;

public record PaymentResponse(
    Long orderId,
    Long paidAmount,
    Long remainingBalance,
    String status,
    String dataTransmission,
    LocalDateTime paidAt
) {
    public static PaymentResponse of(
            Long orderId,
            Long paidAmount,
            Long remainingBalance,
            String status,
            String dataTransmission,
            LocalDateTime paidAt
    ) {
        return new PaymentResponse(
                orderId,
                paidAmount,
                remainingBalance,
                status,
                dataTransmission,
                paidAt
        );
    }
}
