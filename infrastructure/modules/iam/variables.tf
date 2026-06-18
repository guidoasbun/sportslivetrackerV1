variable "project_name" {
  description = "Project name prefix for all resource names"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "kinesis_stream_arn" {
  description = "ARN of the Kinesis stream the producer writes to"
  type        = string
}

variable "events_table_arn" {
  description = "ARN of the DynamoDB Events table"
  type        = string
}

variable "summaries_table_arn" {
  description = "ARN of the DynamoDB Summaries table"
  type        = string
}

variable "bedrock_model_id" {
  description = "Amazon Bedrock model ID used for commentary generation"
  type        = string
}

variable "api_sports_key_arn" {
  description = "Secrets Manager ARN for the API-Sports API key"
  type        = string
}

variable "cognito_google_client_id_arn" {
  description = "Secrets Manager ARN for the Google OAuth client ID"
  type        = string
}

variable "cognito_google_client_secret_arn" {
  description = "Secrets Manager ARN for the Google OAuth client secret"
  type        = string
}

variable "ecr_producer_repository_arn" {
  description = "ARN of the ECR repository for the producer image"
  type        = string
}

variable "ecr_api_repository_arn" {
  description = "ARN of the ECR repository for the api image"
  type        = string
}

variable "ecr_frontend_repository_arn" {
  description = "ARN of the ECR repository for the frontend image"
  type        = string
}

variable "github_org" {
  description = "GitHub organization or username for the OIDC trust condition"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name for the OIDC trust condition"
  type        = string
}

variable "create_github_oidc_provider" {
  description = "Whether to create the GitHub OIDC provider. Only one can exist per AWS account — set false in prod once dev has created it."
  type        = bool
  default     = true
}
