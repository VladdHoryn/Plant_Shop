package com.plantshop.inventory.dto;

public record InventoryResponse(Long plantId, int quantity, int reserved, int available) {
}
