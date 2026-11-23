-- Inventory service schema. This database is private to inventory-service.

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    sku         VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE stock (
    product_id BIGINT PRIMARY KEY REFERENCES products(id),
    available  INTEGER NOT NULL CHECK (available >= 0),
    version    BIGINT  NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id         UUID PRIMARY KEY,
    order_id   UUID NOT NULL,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    status     VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_reservations_order_id ON reservations(order_id);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
