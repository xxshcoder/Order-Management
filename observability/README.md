# Observability

Metrics, dashboards, alerts and log search for the Order Management platform.
This directory contains **configuration only**; the Compose definitions that
mount these files live in `deploy/docker-compose.yml`.

## Endpoints

| Component | Host URL | In-container address |
|---|---|---|
| Prometheus | http://localhost:9090 | `prometheus:9090` |
| Grafana | http://localhost:3000 | `grafana:3000` |
| Elasticsearch | http://localhost:9200 | `elasticsearch:9200` |
| Kibana | http://localhost:5601 | `kibana:5601` |
| Logstash (beats) | localhost:5044 | `logstash:5044` |
| Logstash (HTTP JSON) | localhost:5045 | `logstash:5045` |

Every service exposes Spring Boot Actuator at `/actuator/health`,
`/actuator/prometheus` and `/actuator/metrics`:

| Service | Port | Scrape target |
|---|---|---|
| api-gateway | 8080 | `api-gateway:8080` |
| order-service | 8081 | `order-service:8081` |
| inventory-service | 8082 | `inventory-service:8082` |
| notification-service | 8083 | `notification-service:8083` |

## Mount points expected by Compose

```yaml
# prometheus
./observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
./observability/alerts.yml:/etc/prometheus/alerts.yml:ro

# grafana
./observability/grafana/datasources/datasources.yml:/etc/grafana/provisioning/datasources/datasources.yml:ro
./observability/grafana/dashboards/dashboards.yml:/etc/grafana/provisioning/dashboards/dashboards.yml:ro
./observability/grafana/dashboards/dashboard.json:/var/lib/grafana/dashboards/dashboard.json:ro

# logstash
./observability/logstash/pipeline/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
./observability/logstash/logstash.yml:/usr/share/logstash/config/logstash.yml:ro

# elasticsearch / kibana
./observability/elasticsearch/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml:ro
./observability/kibana/kibana.yml:/usr/share/kibana/config/kibana.yml:ro
```

Datasources use the UIDs `prometheus` and `elasticsearch`, which match the
panel datasource references in `dashboard.json`. The dashboard is provisioned
into the **Order Platform** folder as "Order Platform / Overview".

## Metrics and alerts

Panels: request rate, 5xx error rate, p95 latency, JVM heap, Kafka consumer
lag, cache hit ratio, business event throughput and outbox backlog.

Custom Micrometer names → Prometheus names:

| Micrometer name | Prometheus name |
|---|---|
| `orders.created.total` | `orders_created_total` |
| `orders.confirmed.total` | `orders_confirmed_total` |
| `orders.cancelled.total` | `orders_cancelled_total` |
| `reservations.failed.total` | `reservations_failed_total` |
| `outbox.backlog` | `outbox_backlog` |
| `cache.hits.total` | `cache_hits_total` |
| `cache.misses.total` | `cache_misses_total` |

`alerts.yml` defines:

- `HighHttpErrorRate` — 5xx ratio > 5% for **5 minutes** (required rule).
- `ServiceDown` — Actuator target unreachable for 1 minute.
- `OutboxBacklogGrowing` / `OutboxBacklogCritical` — unpublished events.
- `KafkaConsumerLagHigh` — lag > 1000 for 10 minutes; matches both the
  Micrometer Kafka binder (`kafka_consumer_fetch_manager_records_lag`) and a
  possible `kafka_consumergroup_lag` from a JMX exporter.
- `ReservationFailureSpike`, `OrderThroughputCollapsed` — business signals.

Alertmanager discovery uses DNS for a service named `alertmanager:9093`. If
that container is absent, rules are still evaluated and no connection errors
are logged. Add the service to receive notifications.

## Logs and correlation IDs

The gateway generates `X-Correlation-Id` when a request has none and copies it
into every Kafka event and the MDC of every service. Structured JSON logs go
Filebeat → Logstash (beats:5044 or HTTP:5045) → Elasticsearch index
`orderplatform-logs-YYYY.MM.dd` → Kibana.

1. Apply the index template once so `correlationId` is a keyword:

   ```bash
   curl -X PUT "http://localhost:9200/_index_template/orderplatform-logs" \
     -H 'Content-Type: application/json' \
     --data-binary @observability/elasticsearch/index-template.json
   ```

2. In Kibana (http://localhost:5601), go to **Stack Management → Saved
   Objects → Import** and import `kibana/saved-objects.ndjson`. It creates the
   `orderplatform-logs-*` data view and a "Order Platform logs by
   correlationId" saved search.

3. Open **Discover**, select the saved search, and replace the KQL filter with
   the ID you want to trace:

   ```
   correlationId : "6d4a5c1e-8f2b-4e0a-9a11-0d7f3e2b1c44"
   ```

   To find the ID, send a request with `-H 'X-Correlation-Id: <uuid>'` (or read
   the `X-Correlation-Id` response header when the gateway generated one),
   then search for that value. The same value appears in the `correlationId`
   field of every `order.created`, `stock.reserved`/`stock.rejected` and
   `order.confirmed` event, so the full saga can be followed in order.

The Logstash filter copies MDC `correlationId` (plus common header spellings)
to a top-level field. If a shipper nests the event under `app`, that variant
is handled too.
