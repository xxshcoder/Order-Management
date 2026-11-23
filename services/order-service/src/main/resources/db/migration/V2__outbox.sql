-- Transactional outbox: order.created / order.confirmed / order.cancelled rows are
-- inserted in exactly the same transaction as the corresponding order state change.
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    topic        VARCHAR(128) NOT NULL,
    payload      JSONB NOT NULL,
    status       VARCHAR(16) NOT NULL CHECK (status IN ('NEW', 'SENT')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at      TIMESTAMPTZ
);

CREATE INDEX idx_outbox_new ON outbox_events(status, created_at) WHERE status = 'NEW';
