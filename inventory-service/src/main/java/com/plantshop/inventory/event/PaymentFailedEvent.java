package com.plantshop.inventory.event;

public record PaymentFailedEvent(Long orderId, String reason) {
}
