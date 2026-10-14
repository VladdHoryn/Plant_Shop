package com.plantshop.order.dto;

import com.plantshop.order.entity.OrderStatus;
import com.plantshop.order.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerEmail,
        String shippingAddress,
        PaymentMethod paymentMethod,
        OrderStatus status,
        BigDecimal totalAmount,
        String failureReason,
        List<OrderItemResponse> items,
        Instant createdAt
) {
    public record OrderItemResponse(Long plantId, String plantName, int quantity, BigDecimal unitPrice,
                                    BigDecimal lineTotal) {
    }
}
