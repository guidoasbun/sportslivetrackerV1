output "events_table_name" {
  description = "Name of the events DynamoDB table"
  value       = aws_dynamodb_table.events.name
}

output "events_table_arn" {
  description = "ARN of the events DynamoDB table"
  value       = aws_dynamodb_table.events.arn
}

output "events_gsi_arn" {
  description = "ARN of the events table GSI (used in IAM policies)"
  value       = "${aws_dynamodb_table.events.arn}/index/sport-type-timestamp-index"
}

output "summaries_table_name" {
  description = "Name of the summaries DynamoDB table"
  value       = aws_dynamodb_table.summaries.name
}

output "summaries_table_arn" {
  description = "ARN of the summaries DynamoDB table"
  value       = aws_dynamodb_table.summaries.arn
}

output "summaries_gsi_arn" {
  description = "ARN of the summaries table GSI (used in IAM policies)"
  value       = "${aws_dynamodb_table.summaries.arn}/index/event-id-index"
}
