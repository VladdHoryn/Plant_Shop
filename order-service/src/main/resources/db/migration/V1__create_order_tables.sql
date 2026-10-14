CREATE TABLE orders
(
    id               BIGSERIAL PRIMARY KEY,
    customer_email   VARCHAR(255)   NOT NULL,
    shipping_address VARCHAR(500)   NOT NULL,
    payment_method   VARCHAR(30)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total_amount     NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_email ON orders (customer_email);

CREATE TABLE order_items
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    plant_id   BIGINT         NOT NULL,
    plant_name VARCHAR(150)   NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
