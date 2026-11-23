# ADR-005: ECS Fargate instead of EKS for the AWS compute layer

- **Status:** Accepted
- **Date:** 2026-11-23

## Context
The Terraform layer needs a container compute platform. EKS offers full Kubernetes parity with the local manifests; ECS Fargate is simpler.

## Decision
Use **ECS Fargate** with a single task definition containing the four containers, Cloud Map service discovery and an ALB for the gateway. Each container starts at 0.25 vCPU / 512 MB. No NAT gateway; RDS is single-AZ `db.t3.micro`.

## Consequences
- Much lower cost and operational burden than an EKS control plane plus managed node group; fits the "smallest AWS sizes" rule.
- The Kubernetes manifests in `deploy/k8s/` remain the deployment target for teams that already run a cluster; they are not used by Terraform.
- Fargate cold starts and the lack of DaemonSets mean log/metric collection must be done with the AWS observability stack rather than the Compose ELK stack.
