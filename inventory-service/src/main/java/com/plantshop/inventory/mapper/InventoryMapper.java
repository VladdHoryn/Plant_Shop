package com.plantshop.inventory.mapper;

import com.plantshop.inventory.dto.InventoryResponse;
import com.plantshop.inventory.entity.InventoryItem;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse(item.getPlantId(), item.getQuantity(), item.getReserved(), item.getAvailable());
    }
}
