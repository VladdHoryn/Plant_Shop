-- Customer accounts. Children reference users without ON DELETE CASCADE on purpose: the erasure
-- workflow handles every table explicitly, and a user row is never deleted (it becomes a tombstone).
CREATE TABLE users
(
    id                        BIGSERIAL PRIMARY KEY,
    email                     VARCHAR(255) NOT NULL UNIQUE,
    full_name                 VARCHAR(150),
    phone                     VARCHAR(32),
    -- security data: never exported, cleared on erasure
    password_hash             VARCHAR(100),
    email_verified            BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_token_hash   VARCHAR(64),
    password_reset_token_hash VARCHAR(64),
    password_reset_expires_at TIMESTAMPTZ,
    fraud_review_flag         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- external identity (e.g. social login subject)
    external_provider         VARCHAR(30),
    external_subject_id       VARCHAR(100),
    role                      VARCHAR(30)  NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    anonymized_at             TIMESTAMPTZ,
    UNIQUE (external_provider, external_subject_id)
);

CREATE TABLE user_addresses
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    label       VARCHAR(30)  NOT NULL,
    recipient   VARCHAR(150) NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(2)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_addresses_user_id ON user_addresses (user_id);

CREATE TABLE user_settings
(
    user_id    BIGINT PRIMARY KEY REFERENCES users (id),
    language   VARCHAR(5)  NOT NULL,
    currency   VARCHAR(3)  NOT NULL,
    dark_mode  BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Account history: what the customer did and from where (IP address, user agent, free text)
CREATE TABLE user_activity
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    activity_type VARCHAR(40) NOT NULL,
    details       VARCHAR(500),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(255),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_activity_user_id ON user_activity (user_id);

-- Current consent state: one row per user and purpose
CREATE TABLE user_consents
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    purpose        VARCHAR(40) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,
    source         VARCHAR(20) NOT NULL,
    granted_at     TIMESTAMPTZ NOT NULL,
    withdrawn_at   TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, purpose)
);

-- Append-only evidence of every grant and withdrawal: which policy version, when, through which channel
CREATE TABLE consent_history
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    purpose        VARCHAR(40) NOT NULL,
    action         VARCHAR(20) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,
    source         VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consent_history_user_id ON consent_history (user_id);

-- E-mails handed to the (simulated) mail provider. MARKETING ones require consent, TRANSACTIONAL ones do not.
CREATE TABLE email_outbox
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    category   VARCHAR(20)  NOT NULL,
    template   VARCHAR(60)  NOT NULL,
    recipient  VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_outbox_user_id ON email_outbox (user_id);

-- Optional analytics, collected only with OPTIONAL_ANALYTICS consent. user_id becomes NULL on erasure.
CREATE TABLE analytics_events
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users (id),
    event_name  VARCHAR(60) NOT NULL,
    page        VARCHAR(200),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_events_user_id ON analytics_events (user_id);

-- Privacy audit trail. Holds no personal data by design: actor and subject are keyed pseudonyms (HMAC).
CREATE TABLE privacy_audit_events
(
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(40) NOT NULL,
    actor_ref      VARCHAR(40) NOT NULL,
    subject_ref    VARCHAR(40) NOT NULL,
    outcome        VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_privacy_audit_events_subject ON privacy_audit_events (subject_ref);
