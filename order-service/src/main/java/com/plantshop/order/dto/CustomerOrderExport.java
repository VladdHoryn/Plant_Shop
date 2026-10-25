package com.plantshop.order.dto;

import com.plantshop.order.entity.OrderStatus;
import com.plantshop.order.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** An order as it appears in the customer's personal data export (no internal failure details). */
public record CustomerOrderExport(Long orderId, OrderStatus status, PaymentMethod paymentMethod,
                                  BigDecimal totalAmount, String shippingAddress, Instant createdAt,
                                  List<Item> items) {

    public record Item(Long plantId, String plantName, int quantity, BigDecimal unitPrice) {
    }
}
