variable "project_name" {
  description = "Project name prefix for all resource names"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "lambda_execution_role_arn" {
  description = "ARN of the IAM role the Lambda function assumes at runtime"
  type        = string
}

variable "kinesis_stream_arn" {
  description = "ARN of the Kinesis stream that triggers this Lambda"
  type        = string
}

variable "events_table_name" {
  description = "Name of the DynamoDB Events table (passed as env var to the function)"
  type        = string
}

variable "summaries_table_name" {
  description = "Name of the DynamoDB Summaries table (passed as env var to the function)"
  type        = string
}

variable "bedrock_model_id" {
  description = "Amazon Bedrock model ID for commentary generation"
  type        = string
}

variable "memory_size_mb" {
  description = "Lambda memory allocation in MB — Java needs at least 512 for reasonable startup time"
  type        = number
  default     = 512
}

variable "timeout_seconds" {
  description = "Maximum execution time per invocation in seconds"
  type        = number
  default     = 30
}

variable "handler" {
  description = "Fully-qualified Java handler: ClassName::methodName"
  type        = string
  default     = "live.gameshift.lambda.SportEventHandler::handleRequest"
}

variable "kinesis_batch_size" {
  description = "Max number of Kinesis records delivered per Lambda invocation"
  type        = number
  default     = 100
}

variable "kinesis_starting_position" {
  description = "Where to start reading from Kinesis: LATEST (new records only) or TRIM_HORIZON (all existing records)"
  type        = string
  default     = "LATEST"
}
