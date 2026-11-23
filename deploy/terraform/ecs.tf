# ECS Fargate compute: one task definition hosting all four containers, four
# services with Cloud Map private DNS, and an internet-facing ALB for the gateway.
# Fargate was chosen over EKS for lower cost and operational simplicity.

locals {
  apps = {
    api-gateway = {
      name = "api-gateway"
      port = 8080
      db   = null
    }
    order-service = {
      name = "order-service"
      port = 8081
      db   = "orders"
    }
    inventory-service = {
      name = "inventory-service"
      port = 8082
      db   = "stock"
    }
    notification-service = {
      name = "notification-service"
      port = 8083
      db   = null
    }
  }

  ecs_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "ecs-tasks.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })

  common_env = [
    { name = "SPRING_PROFILES_ACTIVE", value = "ecs" },
    { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = var.kafka_bootstrap_servers },
    { name = "SPRING_DATA_REDIS_HOST", value = var.redis_host },
    { name = "APP_CORRELATION_HEADER", value = "X-Correlation-Id" },
    { name = "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", value = "health,info,prometheus,metrics" },
    { name = "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", value = "true" },
    { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=60 -Xss512k -Djava.security.egd=file:/dev/./urandom" }
  ]

  common_secrets = [
    { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.app_secret["jwt_secret"].arn },
    { name = "CUSTOMER_PASSWORD", valueFrom = aws_ssm_parameter.app_secret["customer_password"].arn },
    { name = "ADMIN_PASSWORD", valueFrom = aws_ssm_parameter.app_secret["admin_password"].arn }
  ]

  all_secret_arns = concat(
    [for parameter in aws_ssm_parameter.db_password : parameter.arn],
    [for parameter in aws_ssm_parameter.app_secret : parameter.arn]
  )
}

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-cluster"
  }
}

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.project_name}-${var.environment}"
  retention_in_days = var.log_retention_days
}

# SecureString parameters keep secrets out of the task definition JSON.
resource "aws_ssm_parameter" "db_password" {
  for_each = local.databases

  name        = "/${var.project_name}/${var.environment}/db/${each.key}/password"
  description = "RDS password for the ${each.key} database (template value)"
  type        = "SecureString"
  value       = each.value.password
  overwrite   = true

  tags = {
    Service = each.key
  }
}

resource "aws_ssm_parameter" "app_secret" {
  for_each = {
    jwt_secret        = var.jwt_secret
    customer_password = var.customer_password
    admin_password    = var.admin_password
  }

  name        = "/${var.project_name}/${var.environment}/app/${each.key}"
  description = "Application secret: ${each.key} (template value)"
  type        = "SecureString"
  value       = each.value
  overwrite   = true
}

resource "aws_iam_role" "execution" {
  name               = "${var.project_name}-${var.environment}-ecs-execution"
  assume_role_policy = local.ecs_assume_role_policy
}

resource "aws_iam_role_policy_attachment" "execution_ecs" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_ssm" {
  statement {
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = local.all_secret_arns
  }
}

resource "aws_iam_role_policy" "execution_ssm" {
  name   = "ssm-parameter-read"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_ssm.json
}

resource "aws_iam_role" "task" {
  name               = "${var.project_name}-${var.environment}-ecs-task"
  assume_role_policy = local.ecs_assume_role_policy
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project_name}-${var.environment}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(length(local.apps) * var.fargate_cpu)
  memory                   = tostring(length(local.apps) * var.fargate_memory)
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    for key, app in local.apps : {
      name      = app.name
      image     = "${aws_ecr_repository.apps[key].repository_url}:${var.image_tag}"
      essential = true
      cpu       = var.fargate_cpu
      memory    = var.fargate_memory

      portMappings = [
        {
          containerPort = app.port
          hostPort      = app.port
          protocol      = "tcp"
        }
      ]

      environment = concat(local.common_env, app.db != null ? [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.main[app.db].address}:${aws_db_instance.main[app.db].port}/${aws_db_instance.main[app.db].db_name}"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = local.databases[app.db].username
        }
      ] : [])

      secrets = concat(local.common_secrets, app.db != null ? [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = aws_ssm_parameter.db_password[app.db].arn
        }
      ] : [])

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = app.name
        }
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-${var.environment}-task"
  }
}

# Private DNS lets services address each other by name inside the VPC,
# e.g. http://order-service.order-platform.dev.local:8081.
resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${var.project_name}.${var.environment}.local"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "apps" {
  for_each = local.apps

  name = each.value.name

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_lb" "gateway" {
  name               = "${var.project_name}-${var.environment}-gw"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = local.public_subnet_ids

  tags = {
    Name = "${var.project_name}-${var.environment}-gw-alb"
  }
}

resource "aws_lb_target_group" "gateway" {
  name        = "${var.project_name}-${var.environment}-gw-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  deregistration_delay = 30

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-gw-tg"
  }
}

resource "aws_lb_listener" "gateway_http" {
  load_balancer_arn = aws_lb.gateway.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}

resource "aws_ecs_service" "apps" {
  for_each = local.apps

  name            = "${var.project_name}-${var.environment}-${each.value.name}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = each.key == "api-gateway" ? 60 : null

  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.apps[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = each.key == "api-gateway" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.gateway.arn
      container_name   = "api-gateway"
      container_port   = 8080
    }
  }

  depends_on = [aws_lb_listener.gateway_http]

  tags = {
    Service = each.value.name
  }
}
