package com.plantshop.inventory.repository;

import com.plantshop.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findAllByPlantIdIn(Collection<Long> plantIds);

    /** Row locks taken in plant-id order so concurrent reservations cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.plantId in :plantIds order by i.plantId")
    List<InventoryItem> findAllForUpdate(@Param("plantIds") Collection<Long> plantIds);
}
