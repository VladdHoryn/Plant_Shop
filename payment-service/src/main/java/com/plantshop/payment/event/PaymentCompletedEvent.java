package com.plantshop.payment.event;

public record PaymentCompletedEvent(Long orderId, Long paymentId, String transactionId) {
}
