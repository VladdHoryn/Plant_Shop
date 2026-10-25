package com.plantshop.order.mapper;

import com.plantshop.order.dto.CustomerOrderExport;
import com.plantshop.order.dto.OrderResponse;
import com.plantshop.order.entity.Order;
import com.plantshop.order.entity.OrderItem;
import com.plantshop.order.event.OrderCreatedEvent;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getPaymentMethod(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getFailureReason(),
                order.getItems().stream().map(OrderMapper::toResponse).toList(),
                order.getCreatedAt());
    }

    private static OrderResponse.OrderItemResponse toResponse(OrderItem item) {
        return new OrderResponse.OrderItemResponse(
                item.getPlantId(), item.getPlantName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }

    public static CustomerOrderExport toExport(Order order) {
        return new CustomerOrderExport(order.getId(), order.getStatus(), order.getPaymentMethod(), order.getTotalAmount(),
                order.getShippingAddress(), order.getCreatedAt(),
                order.getItems().stream()
                        .map(i -> new CustomerOrderExport.Item(i.getPlantId(), i.getPlantName(), i.getQuantity(),
                                i.getUnitPrice()))
                        .toList());
    }

    /** The event carries no customer data: no saga step needs it, and Kafka keeps messages after an erasure. */
    public static OrderCreatedEvent toCreatedEvent(Order order) {
        return new OrderCreatedEvent(
                order.getId(),
                order.getTotalAmount(),
                order.getPaymentMethod().name(),
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.Item(i.getPlantId(), i.getQuantity()))
                        .toList());
    }
}
