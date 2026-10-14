package com.plantshop.inventory.event;

public record PaymentCompletedEvent(Long orderId, Long paymentId, String transactionId) {
}
