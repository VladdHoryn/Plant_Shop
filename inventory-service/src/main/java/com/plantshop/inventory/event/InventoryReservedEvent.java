package com.plantshop.inventory.event;

import java.math.BigDecimal;

public record InventoryReservedEvent(Long orderId, String customerEmail, BigDecimal totalAmount, String paymentMethod) {
}
