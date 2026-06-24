data "aws_region" "current" {}

# ──────────────────────────────────────────────────────────────
# CloudWatch log groups
# Each container writes stdout/stderr here. 30-day retention
# keeps costs low while preserving enough history for debugging.
# ──────────────────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "producer" {
  name              = "/ecs/${var.project_name}-${var.environment}-producer"
  retention_in_days = 30

  tags = {
    Name        = "/ecs/${var.project_name}-${var.environment}-producer"
    Environment = var.environment
  }
}

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/ecs/${var.project_name}-${var.environment}-frontend"
  retention_in_days = 30

  tags = {
    Name        = "/ecs/${var.project_name}-${var.environment}-frontend"
    Environment = var.environment
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.project_name}-${var.environment}-api"
  retention_in_days = 30

  tags = {
    Name        = "/ecs/${var.project_name}-${var.environment}-api"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# ECS cluster
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.environment}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-cluster"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Security group for ECS tasks
# Inbound: only the ALB can send traffic to the container port.
# Outbound: unrestricted so tasks can reach ECR, Kinesis, DynamoDB
# through the NAT gateway in the private subnets.
# ──────────────────────────────────────────────────────────────
resource "aws_security_group" "ecs_tasks" {
  name        = "${var.project_name}-${var.environment}-ecs-tasks-sg"
  description = "Allow inbound from ALB only; allow all outbound"
  vpc_id      = var.vpc_id

  ingress {
    description     = "From ALB to frontend (Next.js)"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  ingress {
    description     = "From ALB to API (Spring Boot)"
    from_port       = var.api_container_port
    to_port         = var.api_container_port
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-ecs-tasks-sg"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Producer task definition
# Spring Boot service that polls API-Sports and writes to Kinesis.
# No port mappings — it sends traffic, never receives it.
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "producer" {
  family                   = "${var.project_name}-${var.environment}-producer"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.producer_cpu
  memory                   = var.producer_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.producer_task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "producer"
    image     = "${var.ecr_producer_repository_url}:latest"
    essential = true

    environment = [
      { name = "ENVIRONMENT",         value = var.environment },
      { name = "KINESIS_STREAM_NAME", value = var.kinesis_stream_name },
      { name = "AWS_REGION",          value = data.aws_region.current.name },
    ]

    secrets = [
      { name = "API_SPORTS_KEY", valueFrom = var.api_sports_key_arn }
    ]

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.producer.name
        "awslogs-region"        = data.aws_region.current.name
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = {
    Name        = "${var.project_name}-${var.environment}-producer"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Producer ECS service
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_service" "producer" {
  name            = "${var.project_name}-${var.environment}-producer"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.producer.arn
  desired_count   = var.producer_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-producer"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Frontend task definition
# Next.js app that serves the dashboard and reads from DynamoDB.
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "frontend" {
  family                   = "${var.project_name}-${var.environment}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.frontend_task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "frontend"
    image     = "${var.ecr_frontend_repository_url}:latest"
    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "ENVIRONMENT", value = var.environment },
      { name = "AWS_REGION",  value = data.aws_region.current.name },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.frontend.name
        "awslogs-region"        = data.aws_region.current.name
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = {
    Name        = "${var.project_name}-${var.environment}-frontend"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Frontend ECS service
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_service" "frontend" {
  name            = "${var.project_name}-${var.environment}-frontend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = var.frontend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "frontend"
    container_port   = var.container_port
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-frontend"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# API task definition
# Spring Boot WebSocket/SSE service that reads DynamoDB and
# streams events to the Next.js frontend. Listens on port 8080.
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "api" {
  family                   = "${var.project_name}-${var.environment}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.api_task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${var.ecr_api_repository_url}:latest"
    essential = true

    portMappings = [{
      containerPort = var.api_container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "ENVIRONMENT",          value = var.environment },
      { name = "AWS_REGION",           value = data.aws_region.current.name },
      { name = "EVENTS_TABLE_NAME",    value = var.events_table_name },
      { name = "SUMMARIES_TABLE_NAME", value = var.summaries_table_name },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = data.aws_region.current.name
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])

  tags = {
    Name        = "${var.project_name}-${var.environment}-api"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# API ECS service
# ──────────────────────────────────────────────────────────────
resource "aws_ecs_service" "api" {
  name            = "${var.project_name}-${var.environment}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.api_target_group_arn
    container_name   = "api"
    container_port   = var.api_container_port
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-api"
    Environment = var.environment
  }
}
