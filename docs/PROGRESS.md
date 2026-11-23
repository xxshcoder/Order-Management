# Build Progress

Legend: `[x]` complete **as source**, `[v]` verified by running the plan's Check command, `[ ]` not started.
The local environment lacks Java/Maven/Docker/kubectl/Terraform/k6 (see `docs/BLOCKERS.md`), so verification is tracked separately from implementation.

## Phase 0 — Bootstrap
- [x] `git init`, `.gitignore`, MIT `LICENSE`, root `README.md` stub, `.env.example`, `Makefile`
- [x] Folder layout from §4 (services, deploy, observability, load-tests, docs)
- [x] Maven parent POM with pinned, compatible versions
- [x] `deploy/docker-compose.yml`: 2× PostgreSQL 16, Redis 7, Kafka KRaft, topic init, health checks, profiles
- [x] Four Spring Boot modules parse as a valid multi-module Maven project (manual review)
- [ ] **Check:** `docker compose up -d` healthy + `mvn -q verify -DskipTests` — **NOT VERIFIED (B-001, B-002)**

## Phase 1 — Inventory service foundation
- [x] Flyway V1 migrations (`products`, `stock`, `reservations`, `processed_events`), V2 outbox
- [x] Entities, repositories, service layer, controller, DTOs with Bean Validation
- [x] Global problem-details exception handler (400 / 404 / 409 / 500)
- [x] springdoc OpenAPI at `/swagger-ui.html`
- [x] Unit tests for service layer + `@WebMvcTest` API tests
- [ ] **Check:** create/read products via Swagger + unit tests pass — **NOT VERIFIED (B-001, B-002)**

## Phase 2 — Orders and events
- [x] Order entities, `PENDING`/`CONFIRMED`/`CANCELLED` lifecycle, `Idempotency-Key`, GET endpoints, cancel
- [x] Kafka config in all services: JSON serializers, typed envelope, topic beans (3 partitions), `.DLT` routing
- [x] Inventory `order.created` consumer: transactional, `processed_events` check, sorted `FOR UPDATE` locks, all-or-nothing decrement, reservations
- [x] Order `stock.reserved` / `stock.rejected` consumers with idempotent status updates
- [x] Inventory `order.cancelled` consumer: release reservations + restore stock, idempotent
- [x] Retry with exponential backoff (3 attempts) → `<topic>.DLT`
- [x] Tests: reservation success, insufficient stock, multi-item all-or-nothing, replay safety, concurrent oversell (Testcontainers)
- [ ] **Check:** saga outcome + replay reserves once — **NOT VERIFIED (B-001, B-002)**

## Phase 3 — Reliability and security
- [x] Transactional outbox in order **and** inventory services (`SKIP LOCKED`, ack-before-SENT)
- [x] Gateway: routes, JWT validation, ADMIN role checks, correlation ID generation/propagation, Redis rate limiter
- [x] Services re-validate JWT locally (ADR-008); `dev` profile for local exploration
- [x] Redis `@Cacheable` product reads (TTL 300s), evict on create/stock update; Micrometer cache/outbox metrics
- [x] Testcontainers integration tests: order happy path, rejection, replay, idempotent creation, stock concurrency, Redis cache eviction
- [x] Gateway security tests: 401 anonymous, 403 CUSTOMER on ADMIN route, 200 for ADMIN
- [x] Kill-Kafka scenario documented as an executable script in `docs/DEMO.md`
- [ ] **Check:** kill-Kafka recovery — **NOT VERIFIED (B-002)**

## Phase 4 — Containers and CI/CD
- [x] Multi-stage Dockerfile per service: `maven:3-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`, non-root, layered jar, `HEALTHCHECK` (busybox `wget`)
- [x] Compose profiles (`core`, `observability`); `make up` starts the full system with one command
- [x] `Jenkinsfile`: Checkout → Build → Unit Tests → Integration Tests → Static Analysis → Dependency Scan → Build Images → Push Images → Deploy; JUnit + JaCoCo publishing
- [x] SpotBugs + OWASP dependency-check wired into the Maven/Jenkins build
- [ ] **Check:** `make up` / pipeline execution — **NOT VERIFIED (B-002)**

## Phase 5 — Kubernetes, Terraform, AWS
- [x] `deploy/k8s/`: namespace, configmap, template-only secret, 4 deployments + services, probes, resources, 2 HPAs, ingress, kustomization
- [x] `deploy/terraform/`: S3+DynamoDB state, VPC, SGs, ECR, 2× RDS `db.t3.micro`, ECS Fargate, variables/outputs/tfvars example
- [x] `deploy/terraform/README.md` with apply, image push and **`destroy`** + cost warning
- [x] No `terraform apply` was run (no AWS credentials — B-005)
- [ ] **Check:** `terraform validate`, k8s dry-run — **NOT VERIFIED (B-003, B-004)**

## Phase 6 — Observability and proof
- [x] Actuator + Micrometer everywhere; custom meters (`orders.*`, `reservations.failed`, `outbox.backlog`, cache metrics)
- [x] `observability/prometheus.yml` scrapes all services; `alerts.yml` includes error rate > 5% for 5m
- [x] Provisioned Grafana dashboard (8 panels) + Prometheus/Elasticsearch datasources
- [x] Structured JSON logging (logstash encoder, `logstash` profile), correlation ID in MDC for HTTP and Kafka
- [x] Logstash pipeline → Elasticsearch index template → Kibana saved search filtered by `correlationId`
- [x] k6 scripts: cached/uncached products, order ramp, service-stopped failure probe
- [x] Compose mount paths reconciled with the observability config layout
- [ ] **Check:** dashboards with data, traced correlation ID, real k6 numbers — **NOT VERIFIED (B-002, B-006)**

## Definition of done
- [x] One-command local startup (`make up`) — source ready, not verified
- [x] Automated tests defined on every push — Jenkinsfile present, not executed
- [x] Deployable manifests + Terraform — source ready, not validated
- [x] README complete with diagram; **real numbers remain placeholders** (B-006)
- [x] `docs/DEMO.md` with exact 2–3 minute commands
- [x] ADRs for all design choices (9 ADRs)

## Final report

### What was built
A complete 4-service Java 21 / Spring Boot 3.3.5 platform: API gateway (JWT, roles, correlation IDs, Redis rate limiting), order-service (idempotent order API, saga coordinator, transactional outbox), inventory-service (products, Redis-cached reads, pessimistic locked all-or-nothing reservations, outbox), and an optional notification logger. Plus Flyway migrations, unit and Testcontainers integration tests, multi-stage non-root Docker images, Docker Compose with core/observability profiles, a declarative Jenkins pipeline, Kubernetes manifests, Terraform for AWS ECS Fargate, Prometheus/Grafana/ELK provisioning, k6 load scripts, 9 ADRs, a full README and a demo script.

### What was verified
- Repository initialised with git; all files committed with conventional commit messages (`git log --oneline` shows 3+ commits).
- File trees, YAML/JSON structure and cross-file references reviewed; Compose mount paths were corrected to match the actual observability file layout.
- Static checks reported by the parallel work agents: Grafana dashboard JSON, ES index template and NDJSON parse; all k6 scripts pass `node --check`; all Compose/K8s YAML files parse with PyYAML; Terraform files pass delimiter/quote balance checks.
- **The plan's Check commands themselves were not run** — see blockers.

### What could not be verified and why
| Check | Reason |
|---|---|
| `mvn -q verify -DskipTests`, unit/integration tests, JaCoCo | Java 21 and Maven absent — B-001 |
| `docker compose up -d`, health, `make up`, image builds | Docker absent — B-002 |
| `kubectl apply --dry-run=client` / kubeconform | kubectl absent — B-003 |
| `terraform fmt` / `terraform validate` | Terraform absent — B-004 |
| `terraform apply` | No AWS credentials; explicitly forbidden by PLAN §7 — B-005 |
| Real p95/RPS, dashboards with data, Kibana screenshots, k6 results | No runnable stack — B-006 |

### Suggested next steps
1. Install JDK 21 + Maven and run `mvn -q verify`; fix any compiler warnings found.
2. Install Docker, run `make up`, then execute `docs/DEMO.md` end-to-end and record real numbers in `README.md`.
3. Install Terraform/kubectl and run the documented validate/dry-run commands.
4. Add OpenTelemetry tracing, circuit breakers, `processed_events` retention and a real IdP as described in the README roadmap.
