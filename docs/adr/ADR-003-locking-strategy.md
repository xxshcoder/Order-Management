# ADR-003: Pessimistic row locks for stock reservation

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
Concurrent orders for the same product must never oversell. Optimistic locking (`@Version`) would cause retry storms under contention and requires careful retry handling.

## Decision
Inventory uses `SELECT … FOR UPDATE` (via `@Lock(PESSIMISTIC_WRITE)`) on `stock` rows. For multi-item orders, product IDs are sorted before locking so all transactions acquire locks in the same order and cannot deadlock. Availability is validated for **all** items before any decrement, giving all-or-nothing reservations. The `stock.available >= 0` CHECK constraint is a final safety net.

## Consequences
- Correctness under concurrency is straightforward and testable (`InventorySagaIT.concurrentReservationsNeverOversell`).
- Lock contention serializes hot products; throughput for a single SKU is bounded. A queue-based or Redis-based reservation scheme would be the next step at very high scale.
- Transactions are short: lock → validate → decrement → insert outbox row → commit.
