package com.plantshop.payment.dto;

import com.plantshop.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String method,
        PaymentStatus status,
        String transactionId,
        String failureReason,
        Instant createdAt
) {
}
