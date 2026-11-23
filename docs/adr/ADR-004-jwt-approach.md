# ADR-004: HS256 JWTs issued by a small gateway auth module and verified everywhere

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
We need authentication, two roles (`CUSTOMER`, `ADMIN`) and a simple local demo. A full OAuth2 authorization server would be disproportionate.

## Decision
The gateway exposes `POST /auth/login` for two seeded demo users (`customer1`, `admin1`), issuing HS256 JWTs with a `roles` claim. The signing secret comes from `JWT_SECRET` (`.env`, never committed). The gateway and every downstream service validate the token locally with Spring Security's resource-server support using the same secret. Roles are mapped from the custom `roles` claim to `ROLE_*` authorities.

## Consequences
- No shared session state; services can be restarted independently.
- Key distribution is trivial for a single dev environment; HS256 must be replaced by RS256/ES256 and a real IdP (Keycloak/Cognito) in production.
- Compromise of the shared secret compromises all services — hence the explicit warning in `.env.example`.
