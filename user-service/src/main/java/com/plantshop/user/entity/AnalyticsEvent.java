package com.plantshop.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "analytics_events")
@Getter
@NoArgsConstructor
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null once the user has been erased: the event then only counts towards aggregate statistics. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_name", nullable = false, length = 60)
    private String eventName;

    @Column(length = 200)
    private String page;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public AnalyticsEvent(Long userId, String eventName, String page) {
        this.userId = userId;
        this.eventName = eventName;
        this.page = page;
    }
}
