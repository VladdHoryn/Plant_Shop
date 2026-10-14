package com.plantshop.catalog.repository;

import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.Plant;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class PlantSpecifications {

    private PlantSpecifications() {
    }

    public static Specification<Plant> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Plant> hasCategory(Long categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Plant> hasCareLevel(CareLevel careLevel) {
        return careLevel == null ? null
                : (root, query, cb) -> cb.equal(root.get("careLevel"), careLevel);
    }

    public static Specification<Plant> priceAtMost(BigDecimal maxPrice) {
        return maxPrice == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Plant> nameContains(String name) {
        return name == null || name.isBlank() ? null
                : (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}
