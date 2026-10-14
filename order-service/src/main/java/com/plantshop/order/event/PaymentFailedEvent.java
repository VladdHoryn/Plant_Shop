package com.plantshop.order.event;

public record PaymentFailedEvent(Long orderId, String reason) {
}
