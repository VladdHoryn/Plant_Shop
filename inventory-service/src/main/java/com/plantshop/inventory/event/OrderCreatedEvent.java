package com.plantshop.inventory.event;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        BigDecimal totalAmount,
        String paymentMethod,
        List<Item> items
) {
    public record Item(Long plantId, int quantity) {
    }
}
