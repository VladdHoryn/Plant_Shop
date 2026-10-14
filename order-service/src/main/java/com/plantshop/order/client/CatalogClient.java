package com.plantshop.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/api/catalog/plants/by-ids")
    List<PlantDto> findByIds(@RequestParam("ids") Collection<Long> ids);

    record PlantDto(Long id, String name, BigDecimal price, boolean active) {
    }
}
