CREATE TABLE inventory_items
(
    plant_id   BIGINT PRIMARY KEY,
    quantity   INTEGER     NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved   INTEGER     NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reserved_not_above_quantity CHECK (reserved <= quantity)
);

CREATE TABLE stock_reservations
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    plant_id   BIGINT      NOT NULL REFERENCES inventory_items (plant_id),
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reservation_order_plant UNIQUE (order_id, plant_id)
);

CREATE INDEX idx_reservations_order_id ON stock_reservations (order_id);
