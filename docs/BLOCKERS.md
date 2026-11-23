# Blockers and Environment Limitations

This file is maintained per `PLAN.md` §0.8: when blocked, log the issue, choose the simplest reasonable option, and continue.

## B-001 — Java 21 / Maven toolchain not installed (Phase 0)

- **Impact:** Cannot compile any module or run `mvn -q verify -DskipTests`, unit tests, or JaCoCo locally.
- **Evidence:** `java -version`, `mvn -version` → "not recognized as the name of a cmdlet" in the build environment (Windows PowerShell, 2026-11-23).
- **Decision taken:** Write the complete Maven multi-module source tree with pinned, mutually compatible dependency versions (Spring Boot 3.3.5 / Spring Cloud 2023.0.3 / Java 21). Code is written defensively and reviewed manually. All *Check* commands that require Maven are marked **NOT VERIFIED** in `docs/PROGRESS.md`.
- **Resolution required:** Install JDK 21 and Maven 3.9+, then run `mvn -q verify`. See `docs/PROGRESS.md` for the exact commands.

## B-002 — Docker / Docker Compose not installed (Phase 0)

- **Impact:** Cannot run `docker compose up -d`, health checks, Testcontainers integration tests, image builds, or k6 against a live stack.
- **Evidence:** `docker --version` → "not recognized as the name of a cmdlet".
- **Decision taken:** Author `deploy/docker-compose.yml`, Dockerfiles and Testcontainers-based integration tests as source. Mark all runtime checks as **NOT VERIFIED**.
- **Resolution required:** Install Docker Desktop with Compose v2 and re-run the phase checks.

## B-003 — kubectl / kind / minikube not installed (Phase 5)

- **Impact:** Cannot run `kubectl apply --dry-run=client` or validate manifests on a cluster.
- **Decision taken:** Produce syntactically valid YAML manifests and document the validation commands for a machine that has kubectl/kubeconform.
- **Resolution required:** Install kubectl (and optionally kubeconform/kind) and run the commands in `deploy/k8s/README.md`.

## B-004 — Terraform CLI not installed (Phase 5)

- **Impact:** Cannot run `terraform fmt` or `terraform validate`.
- **Decision taken:** Produce HCL that follows Terraform 1.8+ conventions and document validation commands. **No `terraform apply` will ever be run** (per `PLAN.md` §7 Phase 5 — requires explicit user credentials).
- **Resolution required:** Install Terraform 1.8+ and run `terraform fmt -check` and `terraform validate`.

## B-005 — AWS credentials not provided (Phase 5)

- **Impact:** Cannot provision infrastructure or run `terraform plan`.
- **Decision taken:** Terraform configuration is written but left unapplied, exactly as required by the plan. `deploy/terraform/README.md` documents apply and destroy with an explicit cost warning.

## B-006 — k6 and observability stack not runnable locally (Phase 6)

- **Impact:** Cannot record real p95/RPS numbers or capture dashboard/Kibana screenshots.
- **Decision taken:** Provide complete k6 scripts and provisioning config. `README.md` uses clearly-labelled placeholders rather than fabricated measurements.
