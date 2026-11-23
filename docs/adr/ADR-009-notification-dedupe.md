# ADR-009: In-memory idempotency for the optional notification service

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
The notification service is optional and has no database (per the plan's data model). Kafka can still deliver `order.confirmed` twice.

## Decision
Use an in-memory `ConcurrentHashMap`-backed set of processed `eventId`s. Since the only side effect is a log line (no email/SMS is actually sent), a duplicate after a restart is harmless and observable.

## Consequences
- Zero infrastructure cost for an optional component.
- Not suitable if real notifications are added: at that point add a `processed_events` table or use the outbox + idempotent sender, and update this ADR.
