package com.plantshop.user.privacy;

/**
 * The erasure policy: what happens to every place that holds data about the user, and why. RETAIN is
 * allowed only with a stated retention reason, and only for records that hold no personal data after
 * the workflow. The erasure response lists these rules with the number of records affected.
 */
public enum ErasureRule {
    USERS("user-service", "users", Action.ANONYMIZE,
            "Row kept as a tombstone so retained records keep a valid foreign key. E-mail replaced by a random unique "
                    + "address, name, phone, external id, credentials, tokens and internal flags cleared."),
    ADDRESSES("user-service", "user_addresses", Action.DELETE,
            "Postal addresses; no reason to keep them."),
    SETTINGS("user-service", "user_settings", Action.DELETE,
            "Preferences; no reason to keep them."),
    ACTIVITY("user-service", "user_activity", Action.DELETE,
            "Account history with IP addresses, user agents and free text (searches, reviews)."),
    EMAIL_OUTBOX("user-service", "email_outbox", Action.DELETE,
            "Copies of sent e-mails, including the recipient address."),
    ANALYTICS("user-service", "analytics_events", Action.ANONYMIZE,
            "Kept for aggregate statistics: the link to the user is removed and the time generalized to the day."),
    CONSENTS("user-service", "user_consents", Action.RETAIN,
            "Retention: evidence of lawful processing in the past. Holds no personal data; every granted consent "
                    + "is withdrawn (source SYSTEM), so the policy gate denies all optional processing."),
    CONSENT_HISTORY("user-service", "consent_history", Action.RETAIN,
            "Retention: append-only proof of when which policy version was accepted and withdrawn (GDPR Art. 7(1)). "
                    + "Holds purpose, version, channel and time only."),
    PRIVACY_AUDIT("user-service", "privacy_audit_events", Action.RETAIN,
            "Retention: accountability record that this erasure was performed. Holds only keyed pseudonyms."),
    ORDERS("order-service", "orders", Action.ANONYMIZE,
            "Retention: accounting records are kept for 1095 days. Customer e-mail and shipping address replaced by "
                    + "neutral placeholders; amounts, items, status and dates kept, so reports stay correct."),
    ORDER_ITEMS("order-service", "order_items", Action.RETAIN,
            "No personal data: plant, quantity, price."),
    PAYMENTS("payment-service", "payments", Action.SKIP,
            "No personal data: amount, method and transaction id, linked only by order id."),
    LOGS("all services", "application logs", Action.SKIP,
            "Personal data is masked and secrets are redacted before a line is written (secure-logging).");

    public enum Action {
        DELETE,
        ANONYMIZE,
        RETAIN,
        SKIP
    }

    private final String system;
    private final String dataset;
    private final Action action;
    private final String rationale;

    ErasureRule(String system, String dataset, Action action, String rationale) {
        this.system = system;
        this.dataset = dataset;
        this.action = action;
        this.rationale = rationale;
    }

    public String system() {
        return system;
    }

    public String dataset() {
        return dataset;
    }

    public Action action() {
        return action;
    }

    public String rationale() {
        return rationale;
    }
}
