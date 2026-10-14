package com.plantshop.catalog.mapper;

import com.plantshop.catalog.dto.CategoryResponse;
import com.plantshop.catalog.dto.PlantRequest;
import com.plantshop.catalog.dto.PlantResponse;
import com.plantshop.catalog.entity.Category;
import com.plantshop.catalog.entity.Plant;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    public static PlantResponse toResponse(Plant plant) {
        return new PlantResponse(
                plant.getId(),
                plant.getName(),
                plant.getLatinName(),
                plant.getDescription(),
                plant.getPrice(),
                toResponse(plant.getCategory()),
                plant.getCareLevel(),
                plant.getLightRequirement(),
                plant.getPotSizeCm(),
                plant.getImageUrl(),
                plant.isActive());
    }

    public static void apply(PlantRequest request, Category category, Plant plant) {
        plant.setName(request.name());
        plant.setLatinName(request.latinName());
        plant.setDescription(request.description());
        plant.setPrice(request.price());
        plant.setCategory(category);
        plant.setCareLevel(request.careLevel());
        plant.setLightRequirement(request.lightRequirement());
        plant.setPotSizeCm(request.potSizeCm());
        plant.setImageUrl(request.imageUrl());
    }
}
