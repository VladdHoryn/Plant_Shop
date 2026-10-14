package com.plantshop.payment.event;

public record PaymentFailedEvent(Long orderId, String reason) {
}
