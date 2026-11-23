# Kubernetes manifests

Plain manifests with no Helm dependency. All runtime configuration is injected
from the `order-platform-config` ConfigMap and the `order-platform-secrets`
Secret; nothing environment-specific is baked into the images.

## Contents

| File | Purpose |
|---|---|
| `namespace.yaml` | `order-platform` namespace |
| `configmap.yaml` | Kafka/Redis endpoints, JDBC URLs, Actuator settings, JVM options |
| `secrets.yaml` | **TEMPLATE ONLY** — placeholder base64 values, not real secrets |
| `*-deployment.yaml` | Four Spring Boot apps, probes, resources, security contexts |
| `*-service.yaml` | ClusterIP Services (gateway exposed on port 80) |
| `*-hpa.yaml` | CPU/memory HPAs for order and inventory (2 → 10 replicas) |
| `ingress.yaml` | nginx Ingress for `orderplatform.local` → gateway |
| `kustomization.yaml` | Single-command apply (excludes the template Secret) |

## Prerequisites

- A Kubernetes 1.27+ cluster and `kubectl` configured against it.
- An nginx Ingress Controller (or edit `ingressClassName` in `ingress.yaml`).
- `metrics-server` installed for the HPAs to receive CPU/memory metrics.
- Images `orderplatform/<service>:latest` pushed to a registry reachable by the
  cluster (override with the `images:` section of `kustomization.yaml`).
- PostgreSQL (two databases: `orders`, `stock`), Redis and Kafka reachable at
  the endpoints in `configmap.yaml`. For AWS deployments point those keys at the
  Terraform outputs (RDS endpoints, ElastiCache/MSK endpoints).

## Deploy

```bash
cd deploy/k8s

# 1. Syntax/dry-run validation (no cluster contact)
kubectl apply --dry-run=client -f namespace.yaml
kubectl apply --dry-run=client -f .          # or: kubectl kustomize . > /dev/null

# 2. Create the namespace and the REAL secret first
kubectl apply -f namespace.yaml
kubectl -n order-platform create secret generic order-platform-secrets \
  --from-literal=JWT_SECRET='<real-secret>' \
  --from-literal=CUSTOMER_PASSWORD='<real-password>' \
  --from-literal=ADMIN_PASSWORD='<real-password>' \
  --from-literal=ORDERS_DB_USERNAME='orderuser' \
  --from-literal=ORDERS_DB_PASSWORD='<real-password>' \
  --from-literal=STOCK_DB_USERNAME='stockuser' \
  --from-literal=STOCK_DB_PASSWORD='<real-password>'

# 3. Apply everything else
kubectl apply -k .

# 4. Verify
kubectl -n order-platform get deploy,svc,hpa,ingress
kubectl -n order-platform rollout status deploy/order-service
kubectl -n order-platform get pods -w
```

`secrets.yaml` is intentionally excluded from `kustomization.yaml`. Do **not**
apply it to a real cluster — its values decode to obvious placeholders. Use
Sealed Secrets, External Secrets Operator or a cloud secret store in production.

## Access

Add `127.0.0.1 orderplatform.local` to `/etc/hosts` and use a port-forward to
the ingress controller, or change `orderplatform.local` in `ingress.yaml` to
your real domain. Gateway health is served at `/actuator/health`.

## Clean up

```bash
kubectl delete -k .
kubectl delete -f namespace.yaml
```

## Validation note for this repository build

`kubectl`, `kubeconform` and a Kubernetes cluster are not available in the
environment where these files were authored. All manifests are plain YAML and
were parsed with a YAML parser locally, but `kubectl apply --dry-run=client`
could not be executed here. Run step 1 above in an environment with `kubectl`
before applying.
