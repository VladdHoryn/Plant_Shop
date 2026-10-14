package com.plantshop.inventory.event;

public record InventoryFailedEvent(Long orderId, String reason) {
}
