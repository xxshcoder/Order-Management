# ADR-007: One PostgreSQL database per service

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
A single shared schema would let services read each other's tables, creating hidden coupling and making independent deployment impossible.

## Decision
Two PostgreSQL 16 instances in Compose (`postgres-orders`, `postgres-stock`), each with its own database and credentials. Order-service owns `orders`, `order_items`, `outbox_events`, `processed_events`; inventory-service owns `products`, `stock`, `reservations`, `outbox_events`, `processed_events`. Services only exchange Kafka events or REST calls through the gateway.

## Consequences
- Schema changes are isolated and Flyway-migrated per service.
- No cross-service JOINs or foreign keys; order items store `product_id` as a plain identifier.
- More infrastructure than a single DB, but far clearer ownership and failure isolation.
