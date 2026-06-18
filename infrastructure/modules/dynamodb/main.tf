resource "aws_dynamodb_table" "events" {
  name         = "${var.project_name}-${var.environment}-events"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "eventId"

  attribute {
    name = "eventId"
    type = "S"
  }

  attribute {
    name = "sportType"
    type = "S"
  }

  attribute {
    name = "eventTimestamp"
    type = "N"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  global_secondary_index {
    name            = "sport-type-timestamp-index"
    hash_key        = "sportType"
    range_key       = "eventTimestamp"
    projection_type = "ALL"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-events"
    Environment = var.environment
  }
}

resource "aws_dynamodb_table" "summaries" {
  name         = "${var.project_name}-${var.environment}-summaries"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "summaryId"

  attribute {
    name = "summaryId"
    type = "S"
  }

  attribute {
    name = "eventId"
    type = "S"
  }

  global_secondary_index {
    name            = "event-id-index"
    hash_key        = "eventId"
    projection_type = "ALL"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-summaries"
    Environment = var.environment
  }
}
