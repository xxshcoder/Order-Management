# ADR-002: Transactional outbox for all event publishing

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
If a service commits a database change and then crashes before publishing the Kafka event, the system is inconsistent. Publishing first has the inverse problem (event without state).

## Decision
Every event is first inserted as a row in `outbox_events` **in the same local database transaction** as the business change. A background publisher (`@Scheduled`, batch 50, `SELECT … FOR UPDATE SKIP LOCKED`) sends rows to Kafka and marks them `SENT` only after the broker acknowledgement. Both order-service and inventory-service use this pattern. No business code calls `KafkaTemplate.send` directly.

## Consequences
- At-least-once delivery; consumers must be idempotent (ADR-006).
- A broker outage blocks the publisher but not order acceptance; backlog is visible via the `outbox.backlog` metric and alert.
- Multiple instances can publish concurrently without duplicate sends thanks to `SKIP LOCKED`.
- Slight latency (default poll 500 ms) between commit and publish.
