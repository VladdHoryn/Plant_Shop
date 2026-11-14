package com.plantshop.order.dto;

import com.plantshop.order.entity.OrderStatus;

/**
 * One answer to "where is my order": the order status and the payment of the order together, so the client does
 * not need a second request to payment-service.
 */
public record OrderTrackingResponse(
        Long orderId,
        OrderStatus orderStatus,
        String paymentStatus,
        String transactionId,
        String failureReason,
        String nextStep
) {
    public static final String PAYMENT_NOT_STARTED = "NOT_STARTED";
}
