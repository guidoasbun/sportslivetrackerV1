variable "project_name" {
  description = "Project name prefix for all resource names"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "lambda_function_name" {
  description = "Name of the Lambda function to monitor"
  type        = string
}

variable "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  type        = string
}

variable "ecs_producer_service_name" {
  description = "Name of the producer ECS service"
  type        = string
}

variable "ecs_api_service_name" {
  description = "Name of the API ECS service"
  type        = string
}

variable "ecs_frontend_service_name" {
  description = "Name of the frontend ECS service"
  type        = string
}

variable "kinesis_stream_name" {
  description = "Name of the Kinesis data stream"
  type        = string
}

variable "dynamodb_events_table_name" {
  description = "Name of the DynamoDB events table"
  type        = string
}

variable "dynamodb_summaries_table_name" {
  description = "Name of the DynamoDB summaries table"
  type        = string
}

variable "aws_region" {
  description = "AWS region for CloudWatch dashboard widgets"
  type        = string
}
