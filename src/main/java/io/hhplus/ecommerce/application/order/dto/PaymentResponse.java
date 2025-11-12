package io.hhplus.ecommerce.application.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PaymentResponse {
    private Long orderId;
    private Long paidAmount;
    private Long remainingBalance;
    private String status;
    private String dataTransmission;
    private LocalDateTime paidAt;

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
