resource "aws_kinesis_stream" "main" {
  name             = "${var.project_name}-${var.environment}-stream"
  retention_period = var.retention_period_hours

  # ON_DEMAND bills per GB written/read instead of a flat ~$11/mo per
  # provisioned shard running 24/7. For this low-volume workload it is
  # substantially cheaper. shard_count must be omitted in ON_DEMAND mode.
  stream_mode_details {
    stream_mode = "ON_DEMAND"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-stream"
    Environment = var.environment
  }
}
