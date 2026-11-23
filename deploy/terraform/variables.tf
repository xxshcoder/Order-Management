variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Short environment name used in resource names."
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project prefix used in resource names."
  type        = string
  default     = "order-platform"
}

variable "vpc_cidr" {
  description = "VPC CIDR block; a /20 gives room for 2 public + 2 private /24 subnets."
  type        = string
  default     = "10.20.0.0/20"
}

variable "az_count" {
  description = "Number of availability zones (2 keeps costs low while remaining HA)."
  type        = number
  default     = 2
}

variable "allowed_ingress_cidr" {
  description = "CIDR allowed to reach the public gateway load balancer. Restrict this in real environments."
  type        = string
  default     = "0.0.0.0/0"
}

variable "orders_db_name" {
  description = "Logical database name for the order service."
  type        = string
  default     = "orders"
}

variable "orders_db_username" {
  description = "Master username for the orders database."
  type        = string
  default     = "orderuser"
}

variable "orders_db_password" {
  description = "Master password for the orders database. Supply via terraform.tfvars or TF_VAR_*."
  type        = string
  sensitive   = true
}

variable "stock_db_name" {
  description = "Logical database name for the inventory service."
  type        = string
  default     = "stock"
}

variable "stock_db_username" {
  description = "Master username for the stock database."
  type        = string
  default     = "stockuser"
}

variable "stock_db_password" {
  description = "Master password for the stock database. Supply via terraform.tfvars or TF_VAR_*."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "Smallest supported RDS PostgreSQL class for this demo."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GiB for each RDS instance."
  type        = number
  default     = 20
}

variable "jwt_secret" {
  description = "HS256 signing secret used by the gateway and services (at least 32 random bytes in production)."
  type        = string
  sensitive   = true
}

variable "customer_password" {
  description = "Seeded demo CUSTOMER user password."
  type        = string
  sensitive   = true
}

variable "admin_password" {
  description = "Seeded demo ADMIN user password."
  type        = string
  sensitive   = true
}

variable "redis_host" {
  description = "Redis endpoint injected into the containers (e.g. an ElastiCache endpoint). Compose is used locally."
  type        = string
  default     = "redis.order-platform.dev.local"
}

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers injected into the containers (e.g. an MSK or self-managed broker list)."
  type        = string
  default     = "b-1.kafka.example.com:9092,b-2.kafka.example.com:9092"
}

variable "image_tag" {
  description = "ECR image tag deployed to ECS."
  type        = string
  default     = "latest"
}

variable "fargate_cpu" {
  description = "CPU units per container (256 = 0.25 vCPU)."
  type        = number
  default     = 256
}

variable "fargate_memory" {
  description = "Memory in MiB per container."
  type        = number
  default     = 512
}

variable "desired_count" {
  description = "Initial ECS task count."
  type        = number
  default     = 1
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention in days."
  type        = number
  default     = 7
}
