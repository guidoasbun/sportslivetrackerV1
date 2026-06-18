resource "aws_kinesis_stream" "main" {
  name             = "${var.project_name}-${var.environment}-stream"
  shard_count      = var.shard_count
  retention_period = var.retention_period_hours

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-stream"
    Environment = var.environment
  }
}
