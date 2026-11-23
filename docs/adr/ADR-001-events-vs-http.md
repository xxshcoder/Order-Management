# ADR-001: Choreography with Kafka events instead of synchronous HTTP for the saga

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
Placing an order must reserve stock in another service. A synchronous call from order-service to inventory-service would couple availability: if inventory is down, order creation fails, and partial retries are hard to reason about.

## Decision
Use a choreography saga over Kafka:
`order.created` → inventory reserves → `stock.reserved` / `stock.rejected` → order confirms/cancels → `order.confirmed` / `order.cancelled`.

Synchronous REST is reserved for client-facing queries and commands through the gateway (`/api/orders`, `/api/inventory/products`). Services do not call each other's HTTP APIs in the order path.

## Consequences
- Order creation is accepted immediately in `PENDING`; the client must poll `GET /orders/{id}` (or later subscribe to notifications) for the final state.
- Failures are recoverable: Kafka retains events and consumers are idempotent.
- No distributed transaction / two-phase commit is needed.
- Debugging requires correlation IDs and event inspection (see observability setup).
