package com.plantshop.payment.event;

import java.math.BigDecimal;

public record InventoryReservedEvent(Long orderId, String customerEmail, BigDecimal totalAmount, String paymentMethod) {
}
