package com.plantshop.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/inventory/check")
    List<StockCheckResponse> check(@RequestBody List<StockCheckRequest> requests);

    record StockCheckRequest(Long plantId, int quantity) {
    }

    record StockCheckResponse(Long plantId, int requested, int available, boolean inStock) {
    }
}
