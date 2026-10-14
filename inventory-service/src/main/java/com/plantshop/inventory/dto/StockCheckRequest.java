package com.plantshop.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockCheckRequest(@NotNull Long plantId, @Positive int quantity) {
}
