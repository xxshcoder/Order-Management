# Deployment assets

This directory contains the local Compose stack, Kubernetes manifests and the
Terraform (AWS ECS Fargate) baseline for the Order Management Platform.

- [`docker-compose.yml`](docker-compose.yml) — full local stack.
- [`init/kafka-topics.sh`](init/kafka-topics.sh) — one-shot topic bootstrap.
- [`k8s/`](k8s/) — namespace, config, templates, deployments, services, HPAs, ingress.
- [`terraform/`](terraform/) — S3/DynamoDB state backend example and ECS Fargate infrastructure.

## 1. Docker Compose (local development)

Run all commands **from this `deploy/` directory**.

```bash
# Core stack only (infrastructure + four applications) - the default
docker compose up -d

# Explicit alias - identical to the default; no service is hidden by this profile
docker compose --profile core up -d

# Core + monitoring/logging
docker compose --profile observability up -d

# Everything
docker compose --profile core --profile observability up -d

# Observe / stop
docker compose ps
docker compose logs -f order-service
docker compose down            # keep named volumes
docker compose down -v         # ALSO DELETE database/Kafka data
```

### Profiles

| Profile | Services | Default? |
|---|---|---|
| *(none / `core`)* | postgres-orders, postgres-stock, redis, kafka, kafka-init, api-gateway, order-service, inventory-service, notification-service | **Yes** — starts with plain `docker compose up` |
| `observability` | prometheus, grafana, elasticsearch, logstash, kibana | No — opt-in with `--profile observability` |

There is no separate service group that must be selected for the core stack: the
core services have no `profiles:` key, so plain `docker compose up` starts the
whole running system. `--profile core` is accepted as an explicit alias.

### Environment variables

All values have development defaults, so the stack starts even with no `.env`
file. To use the repository-root `.env` (from `.env.example`), pass it explicitly:

```bash
docker compose --env-file ../.env up -d
```

Recognised variables (defaults in parentheses):

| Variable | Default | Used by |
|---|---|---|
| `POSTGRES_ORDERS_DB` / `_USER` / `_PASSWORD` | `orders` / `orderuser` / `orderpass` | postgres-orders + order-service |
| `POSTGRES_STOCK_DB` / `_USER` / `_PASSWORD` | `stock` / `stockuser` / `stockpass` | postgres-stock + inventory-service |
| `POSTGRES_ORDERS_PORT` / `POSTGRES_STOCK_PORT` | `5432` / `5433` | host port bindings |
| `REDIS_PORT` | `6379` | host port binding |
| `KAFKA_HOST_PORT` | `29092` | host port binding (clients inside Compose use `kafka:9092`) |
| `JWT_SECRET` | development placeholder | all apps |
| `CUSTOMER_PASSWORD` / `ADMIN_PASSWORD` | `customerpass` / `adminpass` | gateway demo users |
| `APP_CORRELATION_HEADER` | `X-Correlation-Id` (fixed in Compose) | all apps |
| `GATEWAY_PORT`, `ORDER_SERVICE_PORT`, `INVENTORY_SERVICE_PORT`, `NOTIFICATION_SERVICE_PORT` | `8080`, `8081`, `8082`, `8083` | host port bindings |
| `PROMETHEUS_PORT`, `GRAFANA_PORT`, `ELASTICSEARCH_PORT`, `LOGSTASH_PORT`, `KIBANA_PORT` | `9090`, `3000`, `9200`, `5044`, `5601` | observability host ports |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | `admin` / `admin` | Grafana |

**Never use the default secrets outside local development.**

Startup ordering is enforced with `depends_on` conditions: both PostgreSQL
instances and Redis use `pg_isready` / `redis-cli ping`, Kafka uses
`kafka-topics.sh --list`, and `kafka-init` must complete successfully before any
application starts.

### Kafka topics

`kafka-init` creates, with **3 partitions** and replication factor 1:

`order.created`, `stock.reserved`, `stock.rejected`, `order.confirmed`,
`order.cancelled` and matching `.DLT` topics.

Re-run it at any time with `docker compose run --rm kafka-init`.

Inspect from the host:

```bash
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

### Interfaces assumed from other parts of the repository

1. Service Dockerfiles live at `services/<service>/Dockerfile` and build an
   image that listens on the service port and exposes `GET /actuator/health`.
2. Application images must contain either `wget` (BusyBox/GNU) or `curl`; the
   Compose healthchecks use them to probe Actuator. Multi-stage Temurin-based
   Dockerfiles should install one of these in the runtime stage.
3. Observability configuration is provided under `../observability/`:
   `prometheus.yml`, `alerts/`, `grafana/provisioning/`, `grafana/dashboards/`
   and `logstash/pipeline/`. If any of these paths are missing when the
   `observability` profile is enabled, Docker will create an empty directory;
   create the files before enabling that profile.
4. Elasticsearch requires `vm.max_map_count=262144` on the Docker host
   (`sudo sysctl -w vm.max_map_count=262144`).

### Endpoints

| URL | Purpose |
|---|---|
| `http://localhost:8080/actuator/health` | gateway health |
| `http://localhost:8081/actuator/health` | orders health |
| `http://localhost:8082/actuator/health` | inventory health |
| `http://localhost:8083/actuator/health` | notification health |
| `http://localhost:9090` | Prometheus (observability profile) |
| `http://localhost:3000` | Grafana (observability profile) |
| `http://localhost:5601` | Kibana (observability profile) |

## 2. Kubernetes

See [`k8s/README.md`](k8s/README.md) for the apply/verify/delete workflow. The
manifests externalise all configuration through ConfigMap/Secret references and
contain no real credentials.

## 3. Terraform / AWS

See [`terraform/README.md`](terraform/README.md) for bootstrap, apply, image
push and **destroy** instructions, including the cost warning.
