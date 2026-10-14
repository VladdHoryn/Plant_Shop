package com.plantshop.inventory.controller;

import com.plantshop.inventory.dto.InventoryResponse;
import com.plantshop.inventory.dto.StockCheckRequest;
import com.plantshop.inventory.dto.StockCheckResponse;
import com.plantshop.inventory.dto.StockUpdateRequest;
import com.plantshop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public List<InventoryResponse> findAll() {
        return inventoryService.findAll();
    }

    @GetMapping("/{plantId}")
    public InventoryResponse findByPlantId(@PathVariable Long plantId) {
        return inventoryService.findByPlantId(plantId);
    }

    @PutMapping("/{plantId}")
    public InventoryResponse setQuantity(@PathVariable Long plantId, @Valid @RequestBody StockUpdateRequest request) {
        return inventoryService.setQuantity(plantId, request.quantity());
    }

    @PostMapping("/check")
    public List<StockCheckResponse> check(@RequestBody @NotEmpty List<@Valid StockCheckRequest> requests) {
        return inventoryService.check(requests);
    }
}
