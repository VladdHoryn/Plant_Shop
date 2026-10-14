package com.plantshop.order.event;

public record InventoryFailedEvent(Long orderId, String reason) {
}
