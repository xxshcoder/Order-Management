# One ECR repository per Spring Boot service. Images are named
# orderplatform/<service>:latest locally and pushed to these repositories for ECS.

resource "aws_ecr_repository" "apps" {
  for_each = local.apps

  name                 = "${var.project_name}/${var.environment}/${each.value.name}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Service = each.value.name
  }
}

resource "aws_ecr_lifecycle_policy" "apps" {
  for_each = aws_ecr_repository.apps

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only the 10 most recent images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
