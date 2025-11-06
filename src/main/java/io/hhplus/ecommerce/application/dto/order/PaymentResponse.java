package io.hhplus.ecommerce.application.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PaymentResponse {
    private String orderId;
    private Long paidAmount;
    private Long remainingBalance;
    private String status;
    private LocalDateTime paidAt;
}
