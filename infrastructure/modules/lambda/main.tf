# ──────────────────────────────────────────────────────────────
# S3 bucket for Lambda deployment artifacts
# CI/CD uploads the compiled JAR here; Terraform reads it once
# on first apply, then ignores code changes from then on.
# ──────────────────────────────────────────────────────────────
resource "aws_s3_bucket" "lambda_artifacts" {
  bucket        = "${var.project_name}-${var.environment}-lambda-artifacts"
  force_destroy = var.environment == "dev"

  tags = {
    Name        = "${var.project_name}-${var.environment}-lambda-artifacts"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_versioning" "lambda_artifacts" {
  bucket = aws_s3_bucket.lambda_artifacts.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "lambda_artifacts" {
  bucket = aws_s3_bucket.lambda_artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "lambda_artifacts" {
  bucket                  = aws_s3_bucket.lambda_artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Minimal placeholder zip so Terraform can create the Lambda resource
# on first apply before any real code exists.
data "archive_file" "placeholder" {
  type        = "zip"
  output_path = "${path.module}/placeholder.zip"

  source {
    content  = "placeholder — replaced by CI/CD on first deploy"
    filename = "placeholder.txt"
  }
}

resource "aws_s3_object" "lambda_jar" {
  bucket = aws_s3_bucket.lambda_artifacts.id
  key    = "function.zip"
  source = data.archive_file.placeholder.output_path
  etag   = data.archive_file.placeholder.output_md5

  lifecycle {
    ignore_changes = [source, etag]
  }
}

# ──────────────────────────────────────────────────────────────
# Lambda function
# ──────────────────────────────────────────────────────────────
resource "aws_lambda_function" "processor" {
  function_name = "${var.project_name}-${var.environment}-processor"
  role          = var.lambda_execution_role_arn
  runtime       = "java21"
  handler       = var.handler
  memory_size   = var.memory_size_mb
  timeout       = var.timeout_seconds

  s3_bucket         = aws_s3_bucket.lambda_artifacts.id
  s3_key            = aws_s3_object.lambda_jar.key
  s3_object_version = aws_s3_object.lambda_jar.version_id

  environment {
    variables = {
      ENVIRONMENT          = var.environment
      EVENTS_TABLE_NAME    = var.events_table_name
      SUMMARIES_TABLE_NAME = var.summaries_table_name
      BEDROCK_MODEL_ID     = var.bedrock_model_id
    }
  }

  lifecycle {
    ignore_changes = [s3_object_version]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-processor"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Kinesis trigger
# Wires the stream to the function. When Kinesis has records,
# AWS delivers up to batch_size of them to one Lambda invocation.
# ──────────────────────────────────────────────────────────────
resource "aws_lambda_event_source_mapping" "kinesis" {
  event_source_arn               = var.kinesis_stream_arn
  function_name                  = aws_lambda_function.processor.arn
  starting_position              = var.kinesis_starting_position
  batch_size                     = var.kinesis_batch_size
  bisect_batch_on_function_error = true
  maximum_retry_attempts         = 3
}
