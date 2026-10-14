CREATE TABLE payments
(
    id             BIGSERIAL PRIMARY KEY,
    -- One payment attempt per order; also makes redelivered Kafka events harmless
    order_id       BIGINT         NOT NULL UNIQUE,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    method         VARCHAR(30)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    transaction_id VARCHAR(64),
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
