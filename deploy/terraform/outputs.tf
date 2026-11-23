output "alb_dns_name" {
  description = "Public DNS name of the gateway load balancer."
  value       = aws_lb.gateway.dns_name
}

output "ecs_cluster_name" {
  description = "Name of the ECS Fargate cluster."
  value       = aws_ecs_cluster.main.name
}

output "ecr_repository_urls" {
  description = "ECR repository URLs; append :<image_tag> when pushing images."
  value       = { for key, repository in aws_ecr_repository.apps : key => repository.repository_url }
}

output "rds_endpoints" {
  description = "Private RDS endpoints, keyed by database (orders, stock)."
  value       = { for key, database in aws_db_instance.main : key => database.address }
}

output "service_discovery_namespace" {
  description = "Cloud Map private DNS namespace used for inter-service calls."
  value       = aws_service_discovery_private_dns_namespace.main.name
}

output "vpc_id" {
  description = "VPC id created for this environment."
  value       = aws_vpc.main.id
}

output "aws_region" {
  description = "Region where everything was deployed."
  value       = var.aws_region
}

output "aws_account_id" {
  description = "AWS account id used for the deployment."
  value       = data.aws_caller_identity.current.account_id
}
