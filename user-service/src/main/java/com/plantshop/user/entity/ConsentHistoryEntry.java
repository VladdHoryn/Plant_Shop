package com.plantshop.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Append-only proof of a consent change: which policy version, when, through which channel. */
@Entity
@Table(name = "consent_history")
@Getter
@NoArgsConstructor
public class ConsentHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private ConsentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ConsentAction action;

    @Column(name = "policy_version", nullable = false, length = 40, updatable = false)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ConsentSource source;

    @Column(name = "correlation_id", nullable = false, length = 64, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public ConsentHistoryEntry(UserConsent consent, ConsentAction action, String correlationId) {
        this.userId = consent.getUserId();
        this.purpose = consent.getPurpose();
        this.action = action;
        this.policyVersion = consent.getPolicyVersion();
        this.source = consent.getSource();
        this.correlationId = correlationId;
        this.occurredAt = consent.getUpdatedAt();
    }
}
