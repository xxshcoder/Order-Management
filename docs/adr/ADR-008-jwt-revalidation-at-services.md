# ADR-008: Downstream services re-validate JWTs (they do not trust gateway headers)

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
The gateway could forward identity as plain HTTP headers (`X-User-Id`, `X-User-Roles`). That is simple but any client that can reach a service directly could spoof those headers.

## Decision
Every service independently validates the HS256 JWT with the shared secret and derives the user ID and roles from its claims. The gateway still validates tokens for its own route authorization and generates/propagates `X-Correlation-Id`. A local `dev` profile disables security for frictionless Swagger exploration and is never enabled in Compose/Kubernetes.

## Consequences
- Defense in depth: services remain safe even if accidentally exposed.
- Ownership checks (`GET /orders/{id}`, cancel) use the verified `sub` claim.
- Secret rotation must be coordinated across all services (acceptable for dev; use an IdP and JWKS in production).
