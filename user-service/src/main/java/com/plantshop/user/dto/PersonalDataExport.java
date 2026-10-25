package com.plantshop.user.dto;

import com.plantshop.user.entity.ActivityType;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.privacy.DataCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Right-of-access export, contract version {@value #SCHEMA_VERSION}. Every section is always present:
 * an empty list or a null object (listed in {@code omissions}) when there is no data, so consumers never
 * have to guess whether a source was skipped. Timestamps are ISO-8601 instants in UTC.
 */
public record PersonalDataExport(
        Metadata metadata,
        Subject subject,
        List<DataCategory> categories,
        Map<String, String> sources,
        Sections sections,
        List<Omission> omissions,
        List<ExcludedField> excludedFields
) {

    public static final String SCHEMA_VERSION = "1.0";

    /** @param complete false when a source could not be read; see {@code omissions} */
    public record Metadata(String schemaVersion, UUID exportId, Instant generatedAt, String timeZone,
                           String requestedBy, String correlationId, boolean complete) {
    }

    public record Subject(Long userId, String accountStatus, Instant accountCreatedAt) {
    }

    public record Sections(Profile profile, List<AddressEntry> addresses, Settings settings,
                           List<ActivityEntry> activity, Consents consents, List<CommunicationEntry> communications,
                           List<AnalyticsEntry> analytics, List<OrderEntry> orders,
                           List<PrivacyRequestEntry> privacyRequests) {
    }

    public record Profile(String fullName, String email, String phone, boolean emailVerified,
                          LinkedAccount linkedAccount, Instant createdAt, Instant updatedAt) {
    }

    public record LinkedAccount(String provider, String subjectId) {
    }

    public record AddressEntry(String label, String recipient, String line1, String city, String postalCode,
                               String country, Instant createdAt) {
    }

    public record Settings(String language, String currency, boolean darkMode, Instant updatedAt) {
    }

    public record ActivityEntry(ActivityType type, String details, String ipAddress, String userAgent,
                                Instant occurredAt) {
    }

    public record Consents(List<ConsentResponse> current, List<ConsentHistoryResponse> history) {
    }

    public record CommunicationEntry(EmailCategory category, String template, String recipient, Instant sentAt) {
    }

    public record AnalyticsEntry(String event, String page, Instant occurredAt) {
    }

    public record OrderEntry(Long orderId, String status, String paymentMethod, BigDecimal totalAmount,
                             String shippingAddress, Instant createdAt, List<OrderLine> items) {
    }

    public record OrderLine(Long plantId, String plantName, int quantity, BigDecimal unitPrice) {
    }

    public record PrivacyRequestEntry(PrivacyEvent type, AuditOutcome outcome, Instant occurredAt) {
    }

    public record Omission(String section, String reason) {
    }

    public record ExcludedField(String field, String reason) {
    }
}
