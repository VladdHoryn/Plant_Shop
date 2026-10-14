package com.plantshop.catalog.service;

import com.plantshop.catalog.dto.CategoryRequest;
import com.plantshop.catalog.dto.CategoryResponse;
import com.plantshop.catalog.entity.Category;
import com.plantshop.catalog.exception.DuplicateResourceException;
import com.plantshop.catalog.mapper.CatalogMapper;
import com.plantshop.catalog.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("Category '" + request.name() + "' already exists");
        }
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return CatalogMapper.toResponse(categoryRepository.save(category));
    }
}
