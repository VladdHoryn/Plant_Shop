package com.plantshop.catalog.dto;

import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.LightRequirement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlantRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String latinName,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull Long categoryId,
        @NotNull CareLevel careLevel,
        @NotNull LightRequirement lightRequirement,
        @Positive Integer potSizeCm,
        @Size(max = 500) String imageUrl
) {
}
