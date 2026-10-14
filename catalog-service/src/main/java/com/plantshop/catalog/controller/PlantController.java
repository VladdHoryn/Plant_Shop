package com.plantshop.catalog.controller;

import com.plantshop.catalog.dto.PlantRequest;
import com.plantshop.catalog.dto.PlantResponse;
import com.plantshop.catalog.entity.CareLevel;
import com.plantshop.catalog.service.PlantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalog/plants")
@RequiredArgsConstructor
public class PlantController {

    private final PlantService plantService;

    @GetMapping
    public Page<PlantResponse> search(@RequestParam(required = false) String name,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) CareLevel careLevel,
                                      @RequestParam(required = false) BigDecimal maxPrice,
                                      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return plantService.search(name, categoryId, careLevel, maxPrice, pageable);
    }

    @GetMapping("/{id}")
    public PlantResponse findById(@PathVariable Long id) {
        return plantService.findById(id);
    }

    @GetMapping("/by-ids")
    public List<PlantResponse> findByIds(@RequestParam List<Long> ids) {
        return plantService.findByIds(ids);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlantResponse create(@Valid @RequestBody PlantRequest request) {
        return plantService.create(request);
    }

    @PutMapping("/{id}")
    public PlantResponse update(@PathVariable Long id, @Valid @RequestBody PlantRequest request) {
        return plantService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        plantService.deactivate(id);
    }
}
