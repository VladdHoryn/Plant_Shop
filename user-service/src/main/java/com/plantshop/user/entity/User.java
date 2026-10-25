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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    /** Domain reserved by RFC 2606, so anonymized addresses can never reach a real mailbox. */
    public static final String ANONYMIZED_EMAIL_DOMAIN = "@anon.invalid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    /** Internal antifraud marker; never shown to the customer and never exported. */
    @Column(name = "fraud_review_flag", nullable = false)
    private boolean fraudReviewFlag;

    @Column(name = "external_provider", length = 30)
    private String externalProvider;

    @Column(name = "external_subject_id", length = 100)
    private String externalSubjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** "+44 7700 900123" -> "+447700900123" (E.164), so the same number is always stored the same way. */
    public static String normalizePhone(String phone) {
        return phone.replaceAll("[\\s()-]", "");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Irreversibly removes every identifier and credential. The row itself stays as a tombstone so the
     * records that are retained keep a valid foreign key. The replacement e-mail is unique (the column
     * has a unique constraint) and random: it is not derived from the old address, and no mapping back
     * to it is stored anywhere, so this is anonymization, not pseudonymization.
     */
    public void anonymize(Instant now) {
        email = "deleted-" + UUID.randomUUID() + ANONYMIZED_EMAIL_DOMAIN;
        fullName = null;
        phone = null;
        passwordHash = null;
        emailVerified = false;
        verificationTokenHash = null;
        passwordResetTokenHash = null;
        passwordResetExpiresAt = null;
        fraudReviewFlag = false;
        externalProvider = null;
        externalSubjectId = null;
        status = UserStatus.ANONYMIZED;
        anonymizedAt = now;
    }
}
