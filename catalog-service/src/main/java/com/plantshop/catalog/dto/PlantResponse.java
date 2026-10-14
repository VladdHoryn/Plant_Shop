package com.plantshop.catalog.dto;

import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.LightRequirement;

import java.math.BigDecimal;

public record PlantResponse(
        Long id,
        String name,
        String latinName,
        String description,
        BigDecimal price,
        CategoryResponse category,
        CareLevel careLevel,
        LightRequirement lightRequirement,
        Integer potSizeCm,
        String imageUrl,
        boolean active
) {
}
