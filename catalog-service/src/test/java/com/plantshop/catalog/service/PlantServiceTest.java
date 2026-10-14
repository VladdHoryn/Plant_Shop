package com.plantshop.catalog.service;

import com.plantshop.catalog.dto.PlantRequest;
import com.plantshop.catalog.dto.PlantResponse;
import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.Category;
import com.plantshop.catalog.entity.LightRequirement;
import com.plantshop.catalog.entity.Plant;
import com.plantshop.catalog.event.PlantCreatedEvent;
import com.plantshop.catalog.exception.ResourceNotFoundException;
import com.plantshop.catalog.repository.CategoryRepository;
import com.plantshop.catalog.repository.PlantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantServiceTest {

    @Mock
    private PlantRepository plantRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private PlantService plantService;

    private final PlantRequest request = new PlantRequest("Monstera", "Monstera deliciosa", null,
            new BigDecimal("34.99"), 1L, CareLevel.EASY, LightRequirement.BRIGHT_INDIRECT, 17, null);

    @Test
    void createSavesPlantAndPublishesEvent() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Indoor");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(plantRepository.save(any(Plant.class))).thenAnswer(inv -> {
            Plant plant = inv.getArgument(0);
            plant.setId(42L);
            return plant;
        });

        PlantResponse response = plantService.create(request);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.category().name()).isEqualTo("Indoor");
        verify(eventPublisher).publishEvent(new PlantCreatedEvent(42L, "Monstera"));
    }

    @Test
    void createFailsForUnknownCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> plantService.create(request)).isInstanceOf(ResourceNotFoundException.class);
        verify(plantRepository, never()).save(any());
    }
}
