package com.plantshop.user.repository;

import com.plantshop.user.entity.ConsentHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistoryEntry, Long> {

    List<ConsentHistoryEntry> findByUserIdOrderById(Long userId);

    long countByUserId(Long userId);
}
