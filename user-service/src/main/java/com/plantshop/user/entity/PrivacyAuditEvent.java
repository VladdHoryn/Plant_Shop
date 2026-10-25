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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * What happened, to whom, by whom, with what result. Actor and subject are keyed pseudonyms, never
 * e-mails or names, so the audit trail cannot become a hidden copy of erased personal data.
 */
@Entity
@Table(name = "privacy_audit_events")
@Getter
@NoArgsConstructor
public class PrivacyAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private PrivacyEvent eventType;

    @Column(name = "actor_ref", nullable = false, length = 40, updatable = false)
    private String actorRef;

    @Column(name = "subject_ref", nullable = false, length = 40, updatable = false)
    private String subjectRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "correlation_id", nullable = false, length = 64, updatable = false)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public PrivacyAuditEvent(PrivacyEvent eventType, String actorRef, String subjectRef, AuditOutcome outcome,
                             String correlationId) {
        this.eventType = eventType;
        this.actorRef = actorRef;
        this.subjectRef = subjectRef;
        this.outcome = outcome;
        this.correlationId = correlationId;
    }
}
