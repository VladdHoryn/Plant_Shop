package com.plantshop.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(@NotNull Long plantId, @Positive int quantity) {
}
