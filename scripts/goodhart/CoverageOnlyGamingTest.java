package com.plantshop.catalog.service;

import com.plantshop.catalog.dto.CategoryRequest;
import com.plantshop.catalog.dto.PlantRequest;
import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.Category;
import com.plantshop.catalog.entity.LightRequirement;
import com.plantshop.catalog.entity.Plant;
import com.plantshop.catalog.repository.CategoryRepository;
import com.plantshop.catalog.repository.PlantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * GOODHART FIXTURE - deliberately bad, never part of the code base. scripts/goodhart-demo.sh copies it into
 * catalog-service for one run and deletes it again. It executes every service method but asserts nothing,
 * which is how a team "earns" a coverage KPI: the lines turn green, yet a broken implementation would
 * still pass. Exceptions are swallowed for the same reason.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoverageOnlyGamingTest {

    @Mock
    private PlantRepository plantRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private PlantService plantService;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    @SuppressWarnings("unchecked")
    void touchesEverything() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Indoor");
        Plant plant = new Plant();
        plant.setId(7L);
        plant.setCategory(category);
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.of(category));
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(categoryRepository.save(any())).thenReturn(category);
        when(plantRepository.findById(anyLong())).thenReturn(Optional.of(plant));
        when(plantRepository.save(any())).thenReturn(plant);
        when(plantRepository.findAllByIdIn(any())).thenReturn(List.of(plant));
        when(plantRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn((Page<Plant>) new PageImpl<>(List.of(plant)));
        PlantRequest request = new PlantRequest("x", "x", "x", BigDecimal.ONE, 1L, CareLevel.EASY,
                LightRequirement.BRIGHT_INDIRECT, 10, "x");

        quietly(() -> plantService.search("x", 1L, CareLevel.EASY, BigDecimal.TEN, Pageable.unpaged()));
        quietly(() -> plantService.findById(7L));
        quietly(() -> plantService.findByIds(List.of(7L)));
        quietly(() -> plantService.create(request));
        quietly(() -> plantService.update(7L, request));
        quietly(() -> plantService.deactivate(7L));
        quietly(() -> categoryService.findAll());
        quietly(() -> categoryService.create(new CategoryRequest("x", "x")));
        // no assertion anywhere
    }

    private static void quietly(Runnable call) {
        try {
            call.run();
        } catch (RuntimeException ignored) {
            // a failure would lower the "quality" number, so it is hidden
        }
    }
}
