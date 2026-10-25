package com.plantshop.inventory.event;

import java.math.BigDecimal;

public record InventoryReservedEvent(Long orderId, BigDecimal totalAmount, String paymentMethod) {
}
