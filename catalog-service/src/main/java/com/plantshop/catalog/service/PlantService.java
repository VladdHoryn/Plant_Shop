package com.plantshop.catalog.service;

import com.plantshop.catalog.dto.PlantRequest;
import com.plantshop.catalog.dto.PlantResponse;
import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.entity.Category;
import com.plantshop.catalog.entity.Plant;
import com.plantshop.catalog.event.PlantCreatedEvent;
import com.plantshop.catalog.exception.ResourceNotFoundException;
import com.plantshop.catalog.mapper.CatalogMapper;
import com.plantshop.catalog.repository.CategoryRepository;
import com.plantshop.catalog.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static com.plantshop.catalog.repository.PlantSpecifications.hasCareLevel;
import static com.plantshop.catalog.repository.PlantSpecifications.hasCategory;
import static com.plantshop.catalog.repository.PlantSpecifications.isActive;
import static com.plantshop.catalog.repository.PlantSpecifications.nameContains;
import static com.plantshop.catalog.repository.PlantSpecifications.priceAtMost;

@Service
@RequiredArgsConstructor
public class PlantService {

    private final PlantRepository plantRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<PlantResponse> search(String name, Long categoryId, CareLevel careLevel, BigDecimal maxPrice,
                                      Pageable pageable) {
        Specification<Plant> spec = Specification.allOf(
                isActive(), nameContains(name), hasCategory(categoryId), hasCareLevel(careLevel), priceAtMost(maxPrice));
        return plantRepository.findAll(spec, pageable).map(CatalogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PlantResponse findById(Long id) {
        return CatalogMapper.toResponse(getPlant(id));
    }

    /** Bulk lookup used by order-service; includes inactive plants so the caller can reject them explicitly. */
    @Transactional(readOnly = true)
    public List<PlantResponse> findByIds(Collection<Long> ids) {
        return plantRepository.findAllByIdIn(ids).stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional
    public PlantResponse create(PlantRequest request) {
        Plant plant = new Plant();
        CatalogMapper.apply(request, getCategory(request.categoryId()), plant);
        Plant saved = plantRepository.save(plant);
        eventPublisher.publishEvent(new PlantCreatedEvent(saved.getId(), saved.getName()));
        return CatalogMapper.toResponse(saved);
    }

    @Transactional
    public PlantResponse update(Long id, PlantRequest request) {
        Plant plant = getPlant(id);
        CatalogMapper.apply(request, getCategory(request.categoryId()), plant);
        return CatalogMapper.toResponse(plant);
    }

    /** Soft delete: keeps the row so historical orders still reference a valid plant. */
    @Transactional
    public void deactivate(Long id) {
        getPlant(id).setActive(false);
    }

    private Plant getPlant(Long id) {
        return plantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plant", id));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
