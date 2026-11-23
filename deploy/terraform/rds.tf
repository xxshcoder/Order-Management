# Two separate small RDS instances preserve the one-database-per-service rule.
# Both are single-AZ, non-public and configured for cheap teardown (no final snapshot).

locals {
  databases = {
    orders = {
      name     = var.orders_db_name
      username = var.orders_db_username
      password = var.orders_db_password
    }
    stock = {
      name     = var.stock_db_name
      username = var.stock_db_username
      password = var.stock_db_password
    }
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.environment}-db-subnets"
  subnet_ids = local.private_subnet_ids

  tags = {
    Name = "${var.project_name}-${var.environment}-db-subnets"
  }
}

resource "aws_db_instance" "main" {
  for_each = local.databases

  identifier     = "${var.project_name}-${var.environment}-${each.key}"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"

  db_name  = each.value.name
  username = each.value.username
  password = each.value.password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = 0
  deletion_protection       = false
  skip_final_snapshot       = true
  apply_immediately         = true
  auto_minor_version_upgrade = true

  tags = {
    Service = each.key
  }
}
