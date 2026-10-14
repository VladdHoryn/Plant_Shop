package com.plantshop.order.event;

public record PaymentCompletedEvent(Long orderId, Long paymentId, String transactionId) {
}
