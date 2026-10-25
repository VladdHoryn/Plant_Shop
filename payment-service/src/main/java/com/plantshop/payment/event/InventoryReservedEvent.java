package com.plantshop.payment.event;

import java.math.BigDecimal;

public record InventoryReservedEvent(Long orderId, BigDecimal totalAmount, String paymentMethod) {
}
