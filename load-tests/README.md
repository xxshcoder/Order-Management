# Load tests (k6)

Scripts for the Order Management platform. All requests go through the API
gateway at `http://localhost:8080` unless `BASE_URL` is overridden.

## Prerequisites

1. Start the stack (`make up` or `docker compose up -d`).
2. Seed at least one product (with a valid `ADMIN` JWT), e.g. product id `101`.
3. Obtain a `CUSTOMER` JWT (script will log in as `customer1` when `JWT` is not
   set; override `LOGIN_USER` / `LOGIN_PASSWORD` for your environment).

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"password"}' | jq -r .accessToken)
```

`jq` is only used above for convenience; any JSON parser works. The login
response key may be `accessToken`, `token` or `access_token` — all are accepted
by the scripts.

## Environment variables

| Variable | Default | Used by | Purpose |
|---|---|---|---|
| `BASE_URL` | `http://localhost:8080` | all | Gateway base URL |
| `JWT` | (login) | all | Pre-obtained bearer token |
| `LOGIN_USER` / `LOGIN_PASSWORD` | `customer1` / `password` | all | Demo login fallback |
| `LOGIN_PATH` | `/auth/login` | all | Login endpoint |
| `CACHE_ENABLED` | `true` | products | `false` sends `X-Cache-Bypass` and tags results `cache=off` |
| `PAGE` / `PAGE_SIZE` | `0` / `20` | products | Pagination parameters |
| `PRODUCT_ID` | `101` | orders, failure | Product to order |
| `QUANTITY` | `1` | orders | Units per order |
| `RUN_ID` | timestamp | orders, failure | Makes Idempotency-Keys unique per run |
| `WARMUP_VUS`, `RAMP_VUS`, `STEADY_VUS` | see scripts | products, orders | Ramp shape |
| `WARMUP_DURATION`, `RAMP_DURATION`, `STEADY_DURATION`, `RAMP_DOWN_DURATION` | see scripts | products, orders | Stage durations |
| `RATE`, `DURATION`, `MAX_VUS` | `5`, `2m`, `20` | failure | Constant arrival-rate probe |
| `DISABLED_SERVICE` | `inventory-service` | failure | Documentation tag for the faulty dependency |
| `SLEEP_SECONDS` | `1` (`0.5` failure) | all | Pacing per VU iteration |

## Running

Save raw output under `load-tests/results/` (the directory is kept with a
`.gitkeep` file):

```bash
# Cached product reads
k6 run -e JWT="$TOKEN" -e CACHE_ENABLED=true \
  --summary-export=load-tests/results/products-cached-summary.json \
  --out json=load-tests/results/products-cached.json \
  load-tests/products.js

# Uncached product reads (also start inventory-service with
# INVENTORY_CACHE_ENABLED=false for a true Redis-off comparison)
k6 run -e JWT="$TOKEN" -e CACHE_ENABLED=false \
  --summary-export=load-tests/results/products-uncached-summary.json \
  --out json=load-tests/results/products-uncached.json \
  load-tests/products.js

# Order creation ramp test
k6 run -e JWT="$TOKEN" -e PRODUCT_ID=101 -e RUN_ID=run-001 \
  --summary-export=load-tests/results/orders-summary.json \
  --out json=load-tests/results/orders.json \
  load-tests/orders.js
```

Compare the `http_req_duration.p(95)` and request-rate values from the two
product runs, and use `orders-summary.json` for orders/second before errors.

## Failure test

`failure.js` intentionally has **no failing thresholds**: it records what
happens while a dependency is unavailable, including 5xx responses and
timeouts, tagged with `disabled_service`.

```bash
# 1. Bring down one dependency
docker compose stop inventory-service     # or: docker compose stop kafka

# 2. Probe the system
k6 run -e JWT="$TOKEN" -e DISABLED_SERVICE=inventory-service \
  --summary-export=load-tests/results/failure-inventory-summary.json \
  --out json=load-tests/results/failure-inventory.json \
  load-tests/failure.js

# 3. Restore it and watch the outbox drain / orders reach CONFIRMED
docker compose start inventory-service
curl -s http://localhost:9090/api/v1/query?query=outbox_backlog
```

After the service returns, check in Grafana that `outbox_backlog` falls to
zero, Kafka consumer lag returns to normal and no duplicate reservations were
made (one row per `orderId` in the `reservations` table). Record observations
in the final project documentation.

## Results

- Raw JSON lines and summary JSON files belong in `load-tests/results/`.
- Files in that directory are generated artifacts; keep only representative
  summaries if repository size matters.
