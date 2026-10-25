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
import lombok.Setter;

import java.time.Instant;

/** Current state of one consent; every change is also appended to {@link ConsentHistoryEntry}. */
@Entity
@Table(name = "user_consents")
@Getter
@Setter
@NoArgsConstructor
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConsentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentStatus status;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentSource source;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserConsent(Long userId, ConsentPurpose purpose) {
        this.userId = userId;
        this.purpose = purpose;
    }

    /** @return false if this exact consent (same policy version) is already in force */
    public boolean grant(String version, ConsentSource channel, Instant now) {
        if (status == ConsentStatus.GRANTED && version.equals(policyVersion)) {
            return false;
        }
        status = ConsentStatus.GRANTED;
        policyVersion = version;
        source = channel;
        grantedAt = now;
        withdrawnAt = null;
        updatedAt = now;
        return true;
    }

    /** @return false if the consent is already withdrawn, so a repeated REVOKE records nothing */
    public boolean withdraw(ConsentSource channel, Instant now) {
        if (status != ConsentStatus.GRANTED) {
            return false;
        }
        status = ConsentStatus.WITHDRAWN;
        source = channel;
        withdrawnAt = now;
        updatedAt = now;
        return true;
    }
}
