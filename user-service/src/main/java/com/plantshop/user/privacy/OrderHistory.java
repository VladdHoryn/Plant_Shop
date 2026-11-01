package com.plantshop.user.privacy;

import com.plantshop.user.dto.PersonalDataExport.OrderEntry;

import java.util.List;
import java.util.Objects;

/** The customer's orders as order-service returned them for an export, or the fact that it did not answer. */
public record OrderHistory(List<OrderEntry> entries, boolean sourceAvailable) {

    public static final OrderHistory UNAVAILABLE = new OrderHistory(List.of(), false);

    public OrderHistory {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (!sourceAvailable && !entries.isEmpty()) {
            throw new IllegalArgumentException("A source that did not answer cannot have returned orders");
        }
    }

    public static OrderHistory available(List<OrderEntry> entries) {
        return new OrderHistory(entries, true);
    }
}
