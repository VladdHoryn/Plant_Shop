package com.plantshop.user.privacy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.plantshop.user.privacy.DataCategory.ACTIVITY_HISTORY;
import static com.plantshop.user.privacy.DataCategory.COMMUNICATIONS;
import static com.plantshop.user.privacy.DataCategory.CONSENT_RECORDS;
import static com.plantshop.user.privacy.DataCategory.CONTACT_DATA;
import static com.plantshop.user.privacy.DataCategory.DIRECT_IDENTIFIERS;
import static com.plantshop.user.privacy.DataCategory.PREFERENCES;
import static com.plantshop.user.privacy.DataCategory.PRIVACY_REQUESTS;
import static com.plantshop.user.privacy.DataCategory.TECHNICAL_IDENTIFIERS;
import static com.plantshop.user.privacy.DataCategory.TRANSACTIONS;

/** Every section of the personal data export, where its data comes from and which categories it holds. */
public enum ExportSection {
    PROFILE("profile", "user-service: users", List.of(DIRECT_IDENTIFIERS, CONTACT_DATA)),
    ADDRESSES("addresses", "user-service: user_addresses", List.of(CONTACT_DATA)),
    SETTINGS("settings", "user-service: user_settings", List.of(PREFERENCES)),
    ACTIVITY("activity", "user-service: user_activity", List.of(ACTIVITY_HISTORY, TECHNICAL_IDENTIFIERS)),
    CONSENTS("consents", "user-service: user_consents, consent_history", List.of(CONSENT_RECORDS)),
    COMMUNICATIONS("communications", "user-service: email_outbox", List.of(DataCategory.COMMUNICATIONS)),
    ANALYTICS("analytics", "user-service: analytics_events", List.of(ACTIVITY_HISTORY)),
    ORDERS("orders", "order-service: orders, order_items", List.of(TRANSACTIONS)),
    PRIVACY_REQUESTS("privacyRequests", "user-service: privacy_audit_events", List.of(DataCategory.PRIVACY_REQUESTS));

    /** Section key -> where its data comes from, in export order. The same for every export, so built once. */
    public static final Map<String, String> SOURCES = Collections.unmodifiableMap(Arrays.stream(values())
            .collect(Collectors.toMap(ExportSection::key, ExportSection::source, (first, second) -> first,
                    LinkedHashMap::new)));

    private final String key;
    private final String source;
    private final List<DataCategory> categories;

    ExportSection(String key, String source, List<DataCategory> categories) {
        this.key = key;
        this.source = source;
        this.categories = categories;
    }

    public String key() {
        return key;
    }

    public String source() {
        return source;
    }

    public List<DataCategory> categories() {
        return categories;
    }
}
