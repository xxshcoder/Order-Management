# Build Progress

Legend: `[x]` complete as source, `[v]` verified by running the plan's Check command, `[ ]` not started.
Because the local environment lacks Java/Maven/Docker/kubectl/Terraform (see `docs/BLOCKERS.md`), source completion and verification are tracked separately.

## Phase 0 — Bootstrap
- [x] `git init`, `.gitignore`, MIT `LICENSE`, root `README.md` stub, `.env.example`, `Makefile`
- [x] Folder layout from §4 (in progress as agents finish)
- [x] Maven parent POM with pinned, compatible versions
- [ ] `deploy/docker-compose.yml` with Postgres x2, Redis, Kafka KRaft + health checks — assigned to background agent
- [ ] Verify Java 21 and Maven work — **BLOCKED B-001**
- [ ] Four Spring Boot modules compile — **BLOCKED B-001**
- [ ] **Check:** `docker compose up -d` healthy + `mvn -q verify -DskipTests` — **NOT VERIFIED (B-001, B-002)**

## Phase 1 — Inventory service foundation
- [ ] Flyway V1 migrations
- [ ] Entities, repositories, service, controller, DTOs, mapper
- [ ] Global problem-details exception handler
- [ ] springdoc OpenAPI
- [ ] Unit tests for service layer
- [ ] **Check:** create/read products + unit tests pass — **NOT VERIFIED (B-001, B-002)**

## Phase 2 — Orders and events
- [ ] Order service entities, lifecycle, idempotency, GET/cancel endpoints
- [ ] Kafka config, typed events, topic beans in both services
- [ ] Inventory `order.created` consumer (transactional, locked, all-or-nothing)
- [ ] Order `stock.reserved` / `stock.rejected` consumers
- [ ] Inventory `order.cancelled` consumer (release)
- [ ] Retry with backoff + DLT
- [ ] Reservation / idempotency / concurrency tests
- [ ] **Check:** saga ends CONFIRMED/CANCELLED, replay reserves once — **NOT VERIFIED (B-001, B-002)**

## Phase 3 — Reliability and security
- [ ] Transactional outbox in order + inventory services
- [ ] Gateway routes, JWT validation, role checks, correlation ID, Redis rate limiting
- [ ] Service-to-service identity decision ADR
- [ ] Redis caching + cache metrics
- [ ] Testcontainers integration tests
- [ ] Kill-Kafka failure test
- [ ] **Check:** kill-Kafka scenario completes — **NOT VERIFIED (B-002)**

## Phase 4 — Containers and CI/CD
- [ ] Multi-stage non-root Dockerfile per service
- [ ] Compose profiles + one-command startup
- [ ] `Jenkinsfile`
- [ ] Checkstyle/SpotBugs + OWASP dependency-check
- [ ] **Check:** `make up`, pipeline logic — **PARTIALLY NOT VERIFIED (B-002)**

## Phase 5 — Kubernetes, Terraform, AWS
- [ ] K8s manifests — assigned to background agent
- [ ] Terraform config — assigned to background agent
- [ ] `deploy/terraform/README.md` with destroy + cost warning
- [ ] **Check:** `terraform validate`, k8s dry-run — **NOT VERIFIED (B-003, B-004)**

## Phase 6 — Observability and proof
- [ ] Metrics, custom meters, Prometheus scrape config
- [ ] Grafana dashboard + alert rules
- [ ] Structured JSON logging + correlation ID in MDC
- [ ] Logstash → Elasticsearch → Kibana
- [ ] k6 scripts
- [ ] **Check:** dashboards with data, traced correlation ID, k6 results — **NOT VERIFIED (B-002, B-006)**

## Definition of done
- [ ] One-command local startup (`make up`) — source ready, not verified
- [ ] Automated tests pass on every push — pipeline defined, not executed
- [ ] Deployable manifests + Terraform validated — source ready, not verified
- [ ] README complete with diagram and **real** measured numbers — placeholders pending B-006
- [ ] `docs/DEMO.md`
- [ ] ADRs for all design choices
