# Demo script (2–3 minutes)

Prerequisites: `make up` has finished, all containers healthy, `jq` and `uuidgen` available (or substitute any JSON parser / UUID generator).

```bash
cd <repo-root>
cp .env.example .env   # if not already done
make up
docker compose -f deploy/docker-compose.yml ps   # wait until all services are healthy
```

## 1. Get tokens

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin123"}' | jq -r .accessToken)

CUSTOMER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"customer123"}' | jq -r .accessToken)

echo "${ADMIN_TOKEN:0:24}...  ${CUSTOMER_TOKEN:0:24}..."
```

## 2. Create a product with 10 units

```bash
curl -s -X POST http://localhost:8080/api/inventory/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"DEMO-1","name":"Demo widget","price":5.00,"initialStock":10}'
```

## 3. Place an order for 3 units

```bash
KEY=$(uuidgen)
ORDER=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"items":[{"productId":1,"quantity":3,"unitPrice":5.00}]}')
echo "$ORDER" | jq .
ID=$(echo "$ORDER" | jq -r .id)
```

## 4. Watch the saga complete

```bash
for i in $(seq 1 10); do
  sleep 1
  curl -s http://localhost:8080/api/orders/$ID -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq -c '{status,total}'
done
```

Expected: `PENDING` → `CONFIRMED`. Stock should now be 7:

```bash
curl -s http://localhost:8080/api/inventory/products/1 -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq '{available}'
```

## 5. Prove idempotency

```bash
# Same key + same user returns the SAME order, no new order or reservation
curl -s -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"items":[{"productId":1,"quantity":3,"unitPrice":5.00}]}' | jq '{id,status}'
```

## 6. Replay a Kafka event (duplicate delivery)

```bash
CORR=demo-replay-$(uuidgen)
EVENT=$(printf '{"eventId":"%s","type":"order.created","occurredAt":"2026-11-23T10:30:00Z","correlationId":"%s","orderId":"%s","items":[{"productId":1,"quantity":3}]}' \
  "$(uuidgen)" "$CORR" "$ID")

docker compose -f deploy/docker-compose.yml exec -T kafka \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic order.created <<<"$EVENT"

sleep 2
curl -s http://localhost:8080/api/inventory/products/1 -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq '{available}'
```

Stock stays at **7** — `processed_events` suppressed the duplicate.

## 7. Kill Kafka mid-flow, then recover

```bash
docker compose -f deploy/docker-compose.yml stop kafka
NEWKEY=$(uuidgen)
NEWORDER=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $NEWKEY" \
  -d '{"items":[{"productId":1,"quantity":1,"unitPrice":5.00}]}')
NEWID=$(echo "$NEWORDER" | jq -r .id)

# Outbox row stays NEW while the broker is down
docker compose -f deploy/docker-compose.yml exec -T postgres-orders \
  psql -U orderuser -d orders -c "SELECT status,count(*) FROM outbox_events GROUP BY status;"

docker compose -f deploy/docker-compose.yml start kafka
sleep 10
curl -s http://localhost:8080/api/orders/$NEWID -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq '{status}'
```

Expected: order ends `CONFIRMED`, the outbox drains to `SENT`, and stock shows exactly one additional unit reserved.

## 8. Trace it and shut down

- Grafana → **Order Platform** dashboard: request rate, p95, outbox backlog, cache hit ratio.
- Kibana → saved search **Correlation ID search**, paste one of the `correlationId` values from the logs.
- Cancel an order and confirm stock is restored:
  `curl -X POST http://localhost:8080/api/orders/$ID/cancel -H "Authorization: Bearer $CUSTOMER_TOKEN"`

```bash
make down
```
