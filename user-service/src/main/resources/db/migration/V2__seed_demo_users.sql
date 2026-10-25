-- Synthetic demo accounts, no real people: e-mails on the reserved .test domain, +44 7700 900xxx numbers
-- (reserved by Ofcom for fiction), IPs from the 203.0.113.0/24 documentation range.
-- Passwords (demo only, listed in README): Alice-Demo-2026, Bob-Demo-2026, Dpo-Demo-2026,
-- Marketing-Demo-2026, Erin-Demo-2026.
INSERT INTO users (email, full_name, phone, password_hash, email_verified, password_reset_token_hash,
                   password_reset_expires_at, fraud_review_flag, external_provider, external_subject_id, role, status)
VALUES ('alice@example.test', 'Alice Demo', '+447700900101',
        '$2a$10$tb2/rV8FAIRMacwyK5rPhu0R1Zz2VvwqhbrPDEv4mSRaIk3ucjJny', TRUE,
        '13d23f1daab546eef3a97eebced90fe5fed439a8f3b0f892a764ed372c8b5976', NOW() + INTERVAL '30 minutes',
        TRUE, 'google', 'google-demo-1001', 'CUSTOMER', 'ACTIVE'),
       ('bob@example.test', 'Bob Demo', '+447700900202',
        '$2a$10$oAH2eDlsaNH2MvQ.UThxoeJ8JZDg.ebojiC/Pbxjw7gYVAhPdhpc2', TRUE, NULL, NULL,
        FALSE, NULL, NULL, 'CUSTOMER', 'ACTIVE'),
       ('dpo@example.test', 'Dana Privacy', '+447700900303',
        '$2a$10$Njkhvn8H3N3nPYTbK4RtNOkCpwHgt/F.S.DvblhNvYfIOvJRNaL.i', TRUE, NULL, NULL,
        FALSE, NULL, NULL, 'PRIVACY_OFFICER', 'ACTIVE'),
       ('marketing@example.test', 'Marta Campaign', '+447700900404',
        '$2a$10$9TPIKBed6Xh2oWE49qwiYO.tCiwLdqG2BhM6vlwScw36xu0iCXZuu', TRUE, NULL, NULL,
        FALSE, NULL, NULL, 'MARKETING', 'ACTIVE'),
       ('erin@example.test', 'Erin Erase', '+447700900505',
        '$2a$10$FS1m5kNEYmlEZFmqNnqRCuzokd4ZV2TCjy/BN1Zt/kf4pXicxSs2q', TRUE, NULL, NULL,
        FALSE, 'google', 'google-demo-1005', 'CUSTOMER', 'ACTIVE');

INSERT INTO user_addresses (user_id, label, recipient, line1, city, postal_code, country)
SELECT id, 'home', 'Alice Demo', '1 Fern Street', 'Kyiv', '01001', 'UA' FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'office', 'Alice Demo', '22 Moss Avenue', 'Lviv', '79000', 'UA' FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'home', 'Erin Erase', '5 Cactus Lane', 'Odesa', '65000', 'UA' FROM users WHERE email = 'erin@example.test';

INSERT INTO user_settings (user_id, language, currency, dark_mode)
SELECT id, 'uk', 'UAH', TRUE FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'en', 'UAH', FALSE FROM users WHERE email = 'bob@example.test'
UNION ALL
SELECT id, 'uk', 'UAH', FALSE FROM users WHERE email = 'erin@example.test';

INSERT INTO user_activity (user_id, activity_type, details, ip_address, user_agent, occurred_at)
SELECT id, 'LOGIN', NULL, '203.0.113.10', 'Mozilla/5.0 (demo)', NOW() - INTERVAL '3 days'
FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'SEARCH', 'monstera for a dark corner', '203.0.113.10', 'Mozilla/5.0 (demo)', NOW() - INTERVAL '3 days'
FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'REVIEW_POSTED', 'Ficus arrived healthy, thanks!', '203.0.113.11', 'PlantShop-Android/2.1 (demo)', NOW() - INTERVAL '1 day'
FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'LOGIN', NULL, '203.0.113.50', 'Mozilla/5.0 (demo)', NOW() - INTERVAL '2 days'
FROM users WHERE email = 'erin@example.test'
UNION ALL
SELECT id, 'SEARCH', 'pet-safe plants', '203.0.113.50', 'Mozilla/5.0 (demo)', NOW() - INTERVAL '2 days'
FROM users WHERE email = 'erin@example.test';

INSERT INTO user_consents (user_id, purpose, status, policy_version, source, granted_at, updated_at)
SELECT id, 'OPTIONAL_ANALYTICS', 'GRANTED', 'analytics-v1', 'WEB', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'
FROM users WHERE email IN ('alice@example.test', 'erin@example.test')
UNION ALL
SELECT id, 'MARKETING_EMAIL', 'GRANTED', 'marketing-v2', 'MOBILE_APP', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'
FROM users WHERE email = 'erin@example.test';

INSERT INTO consent_history (user_id, purpose, action, policy_version, source, correlation_id, occurred_at)
SELECT user_id, purpose, 'GRANTED', policy_version, source, 'seed', granted_at FROM user_consents;

INSERT INTO email_outbox (user_id, category, template, recipient, created_at)
SELECT id, 'TRANSACTIONAL', 'order-confirmation', email, NOW() - INTERVAL '1 day' FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'MARKETING', 'campaign-spring-sale', email, NOW() - INTERVAL '4 days' FROM users WHERE email = 'erin@example.test';

INSERT INTO analytics_events (user_id, event_name, page, occurred_at)
SELECT id, 'plant_viewed', '/plants/3', NOW() - INTERVAL '3 days' FROM users WHERE email = 'alice@example.test'
UNION ALL
SELECT id, 'plant_viewed', '/plants/7', NOW() - INTERVAL '2 days' FROM users WHERE email = 'erin@example.test'
UNION ALL
SELECT id, 'added_to_cart', '/plants/7', NOW() - INTERVAL '2 days' FROM users WHERE email = 'erin@example.test';
