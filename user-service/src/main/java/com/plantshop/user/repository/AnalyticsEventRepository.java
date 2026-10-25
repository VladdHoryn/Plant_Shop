package com.plantshop.user.repository;

import com.plantshop.user.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    List<AnalyticsEvent> findByUserIdOrderByOccurredAtDesc(Long userId);

    long countByUserId(Long userId);

    /**
     * Anonymizes the user's events for aggregate statistics: the link to the user is removed and the
     * timestamp is generalized to the day, so the events cannot be matched to a session in other data.
     */
    @Modifying
    @Query(value = "UPDATE {h-schema}analytics_events SET user_id = NULL, occurred_at = date_trunc('day', occurred_at) "
            + "WHERE user_id = :userId", nativeQuery = true)
    int detachFromUser(Long userId);
}
