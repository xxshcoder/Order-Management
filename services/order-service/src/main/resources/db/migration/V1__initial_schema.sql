-- Order service schema. Private to order-service; inventory has its own database.

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    idempotency_key VARCHAR(255) NOT NULL,
    total           NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_orders_user_id ON orders(user_id);

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
