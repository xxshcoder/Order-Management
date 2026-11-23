# Screenshot placeholders

The plan asks for Grafana dashboard and Kibana saved-search screenshots. They could not be captured in this build environment because Docker and the observability stack are unavailable (see `docs/BLOCKERS.md` B-006).

Expected files once a stack is running:

- `grafana.png` — Grafana → *Order Platform* folder → request rate / error rate / p95 / JVM / Kafka lag / cache hit ratio panels, with live data.
- `kibana.png` — Kibana → *Correlation ID search*, filtered to a `correlationId` produced by `docs/DEMO.md`, showing logs from the gateway, order-service and inventory-service.

Do not commit fabricated screenshots. Replace this note with real images after running the demo.
