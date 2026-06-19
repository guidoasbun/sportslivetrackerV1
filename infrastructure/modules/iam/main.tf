data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  account_id        = data.aws_caller_identity.current.account_id
  region            = data.aws_region.current.name
  bedrock_model_arn = "arn:aws:bedrock:${local.region}::foundation-model/${var.bedrock_model_id}"
}

# ──────────────────────────────────────────────────────────────
# ECS Execution Role  (shared by all ECS tasks)
# ECS itself assumes this role to pull images and read secrets —
# it is NOT the role your application code runs as.
# ──────────────────────────────────────────────────────────────
resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-${var.environment}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-ecs-execution-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "read-secrets"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "secretsmanager:GetSecretValue"
      Resource = [
        var.api_sports_key_arn,
        var.cognito_google_client_id_arn,
        var.cognito_google_client_secret_arn,
      ]
    }]
  })
}

# ──────────────────────────────────────────────────────────────
# Producer ECS Task Role
# The application code running inside the producer container
# assumes this role — it can only write to our Kinesis stream.
# ──────────────────────────────────────────────────────────────
resource "aws_iam_role" "producer_task" {
  name = "${var.project_name}-${var.environment}-producer-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-producer-task-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "producer_kinesis" {
  name = "kinesis-put-record"
  role = aws_iam_role.producer_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "kinesis:PutRecord"
      Resource = var.kinesis_stream_arn
    }]
  })
}

# ──────────────────────────────────────────────────────────────
# Lambda Execution Role
# Lambda assumes this role to: write to DynamoDB, invoke Bedrock,
# and consume records from Kinesis.
# ──────────────────────────────────────────────────────────────
resource "aws_iam_role" "lambda_execution" {
  name = "${var.project_name}-${var.environment}-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-lambda-execution-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "lambda_dynamodb" {
  name = "dynamodb-write"
  role = aws_iam_role.lambda_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "dynamodb:PutItem"
      Resource = [
        var.events_table_arn,
        var.summaries_table_arn,
      ]
    }]
  })
}

resource "aws_iam_role_policy" "lambda_bedrock" {
  name = "bedrock-invoke"
  role = aws_iam_role.lambda_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "bedrock:InvokeModel"
      Resource = [
        "arn:aws:bedrock:*::foundation-model/*",
        "arn:aws:bedrock:*:${local.account_id}:inference-profile/*",
      ]
    }]
  })
}

resource "aws_iam_role_policy" "lambda_kinesis" {
  name = "kinesis-consume"
  role = aws_iam_role.lambda_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "kinesis:GetRecords",
        "kinesis:GetShardIterator",
        "kinesis:DescribeStream",
        "kinesis:ListShards",
        "kinesis:ListStreams",
      ]
      Resource = var.kinesis_stream_arn
    }]
  })
}

# ──────────────────────────────────────────────────────────────
# Frontend/API ECS Task Role
# The frontend container can only read from DynamoDB.
# ──────────────────────────────────────────────────────────────
resource "aws_iam_role" "frontend_task" {
  name = "${var.project_name}-${var.environment}-frontend-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-frontend-task-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "frontend_dynamodb" {
  name = "dynamodb-read"
  role = aws_iam_role.frontend_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:Query",
      ]
      Resource = [
        var.events_table_arn,
        var.summaries_table_arn,
      ]
    }]
  })
}

# ──────────────────────────────────────────────────────────────
# GitHub Actions OIDC
# OIDC = OpenID Connect. GitHub generates a short-lived token per
# workflow run; AWS verifies it and grants the role — no static
# AWS keys ever stored in GitHub secrets.
# ──────────────────────────────────────────────────────────────
resource "aws_iam_openid_connect_provider" "github" {
  count           = var.create_github_oidc_provider ? 1 : 0
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

data "aws_iam_openid_connect_provider" "github_existing" {
  count = var.create_github_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_arn = var.create_github_oidc_provider ? (
    aws_iam_openid_connect_provider.github[0].arn
  ) : (
    data.aws_iam_openid_connect_provider.github_existing[0].arn
  )
}

resource "aws_iam_role" "github_actions" {
  name = "${var.project_name}-${var.environment}-github-actions-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.github_oidc_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_org}/${var.github_repo}:*"
        }
      }
    }]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-github-actions-role"
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "deploy"
  role = aws_iam_role.github_actions.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = [
          var.ecr_producer_repository_arn,
          var.ecr_api_repository_arn,
          var.ecr_frontend_repository_arn,
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
          "ecs:DescribeServices",
        ]
        Resource = "arn:aws:ecs:${local.region}:${local.account_id}:service/${var.project_name}-${var.environment}-*"
      },
      {
        Effect   = "Allow"
        Action   = [
          "lambda:UpdateFunctionCode",
          "lambda:UpdateFunctionConfiguration",
        ]
        Resource = "arn:aws:lambda:${local.region}:${local.account_id}:function:${var.project_name}-${var.environment}-*"
      },
    ]
  })
}
