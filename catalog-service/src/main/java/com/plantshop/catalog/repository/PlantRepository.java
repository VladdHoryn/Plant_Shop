package com.plantshop.catalog.repository;

import com.plantshop.catalog.entity.Plant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface PlantRepository extends JpaRepository<Plant, Long>, JpaSpecificationExecutor<Plant> {

    @EntityGraph(attributePaths = "category")
    List<Plant> findAllByIdIn(Collection<Long> ids);
}
