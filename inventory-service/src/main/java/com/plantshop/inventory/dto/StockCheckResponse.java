package com.plantshop.inventory.dto;

public record StockCheckResponse(Long plantId, int requested, int available, boolean inStock) {
}
