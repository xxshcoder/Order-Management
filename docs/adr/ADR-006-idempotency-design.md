# ADR-006: Two-layer idempotency (API key + event IDs)

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
Clients may retry `POST /orders` after a timeout, and Kafka delivers events at least once. Both can cause duplicates.

## Decision
1. **API layer:** `POST /orders` requires an `Idempotency-Key` header, stored with a UNIQUE constraint on `(user_id, idempotency_key)`. A repeat with the same key returns the original order without creating a new one (including the concurrent-race path handled by the unique constraint).
2. **Event layer:** every consumer checks `processed_events(event_id)` inside the same transaction as its state change, then records the ID before commit. Duplicate `order.created`, `stock.*` or `order.cancelled` events are no-ops.
3. `order.cancelled` release is additionally guarded by reservation status (`RESERVED` → `RELEASED`).

## Consequences
- Duplicate HTTP requests and duplicate Kafka events are both safe.
- Idempotency keys must be unique per user; clients should generate UUIDs.
- `processed_events` grows indefinitely; add a retention job (e.g. delete rows older than 30 days) before high-volume production use.
