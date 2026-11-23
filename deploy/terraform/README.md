# Terraform: AWS ECS Fargate baseline

Small, deliberately minimal AWS footprint for the Order Management Platform.
**ECS Fargate was chosen over EKS** (no control plane cost, no cluster upgrades;
the ADR is written by the documentation owner). Resources created:

- VPC `/20`, 2 public + 2 private `/24` subnets, internet gateway, **no NAT gateway**.
- 2 × RDS PostgreSQL 16 `db.t3.micro`, 20 GB each, single-AZ, private.
- 4 ECR repositories with scan-on-push and a "keep 10 images" lifecycle policy.
- 1 ECS Fargate cluster, 1 task definition with 4 containers
  (0.25 vCPU / 512 MB **per container**), 4 ECS services, Cloud Map private DNS.
- Internet-facing ALB for the gateway, CloudWatch log group, SSM SecureString
  parameters for all secrets, least-privilege execution role.

Redis and Kafka are intentionally **not** provisioned here (MSK would dominate
the bill). Supply `redis_host` and `kafka_bootstrap_servers` from an
ElastiCache/MSK deployment or another environment; the Compose stack provides
both locally.

> ⚠️ **COST WARNING:** These are real, billable AWS resources. A rough estimate
> for `us-east-1` is **USD 100–150 per month** if left running (2 micro RDS
> instances ≈ $25–35, ALB ≈ $16–22, four 0.25 vCPU/512 MB Fargate tasks ≈ $35–40,
> plus ECR/CloudWatch/Cloud Map). Always run `terraform destroy` when finished.

No credentials are stored in this directory. Never commit `terraform.tfvars`,
`backend.hcl` or any real secret.

## Prerequisites

- Terraform >= 1.5.0 and the AWS CLI installed and configured
  (`aws sts get-caller-identity` must succeed).
- An AWS account with permissions for VPC, RDS, ECR, ECS, IAM, ALB, SSM and S3.
- An S3 bucket (versioning + encryption) and a DynamoDB lock table used only
  for remote state. These bootstrap resources are **not** managed by this config.

## 1. Bootstrap the remote state backend

```bash
AWS_REGION=us-east-1
STATE_BUCKET=orderplatform-terraform-state-REPLACE_WITH_ACCOUNT_ID
LOCK_TABLE=orderplatform-terraform-locks

aws s3api create-bucket --bucket "$STATE_BUCKET" --region "$AWS_REGION"
aws s3api put-bucket-versioning --bucket "$STATE_BUCKET" \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket "$STATE_BUCKET" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws dynamodb create-table \
  --table-name "$LOCK_TABLE" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$AWS_REGION"
```

## 2. Configure and apply

```bash
cd deploy/terraform
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars
# Edit both files: real bucket/table names, DB passwords, JWT secret, endpoints.

terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

`terraform apply` outputs the ALB DNS name, ECR repository URLs, RDS endpoints
and the Cloud Map namespace.

## 3. Build and push the images

Run from the repository root so the Maven build context is correct:

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
ECR="$AWS_ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ECR"

for svc in api-gateway order-service inventory-service notification-service; do
  docker build -f "services/$svc/Dockerfile" \
    -t "$ECR/order-platform/dev/$svc:latest" .
  docker push "$ECR/order-platform/dev/$svc:latest"
done
```

Then force a new deployment so ECS pulls the pushed tag:

```bash
aws ecs update-service --cluster order-platform-dev \
  --service order-platform-dev-api-gateway --force-new-deployment
# repeat for the other three services
```

Remember to replace the placeholder `redis_host` / `kafka_bootstrap_servers`
with real endpoints before expecting orders to complete.

## 4. Verify

```bash
curl http://$(terraform output -raw alb_dns_name)/actuator/health
aws ecs list-services --cluster order-platform-dev
aws rds describe-db-instances --query 'DBInstances[*].[DBIdentifier,Endpoint.Address]'
```

## 5. Destroy (IMPORTANT)

```bash
cd deploy/terraform
terraform plan -destroy
terraform destroy
# Confirm with 'yes' when prompted.

# Optionally remove the bootstrap backend resources as well:
# aws dynamodb delete-table --table-name orderplatform-terraform-locks
# aws s3 rb s3://orderplatform-terraform-state-REPLACE_WITH_ACCOUNT_ID --force
```

RDS instances are configured with `skip_final_snapshot = true` and
`deletion_protection = false`, so the databases are deleted without a final
snapshot. ECR repositories use `force_delete = true`. If you need to retain
data, change those settings before applying.

## Validation note for this repository build

Terraform is not installed in the environment where these files were authored,
so `terraform fmt -check` and `terraform validate` could not be executed here.
Run both commands in step 2 before applying to a real account.
