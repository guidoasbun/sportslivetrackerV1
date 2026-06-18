variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name used as a prefix for all resource names"
  type        = string
  default     = "sports-tracker"
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "domain_name" {
  description = "Domain name for the ALB (used when HTTPS is added)"
  type        = string
  default     = ""
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

variable "bedrock_model_id" {
  description = "Amazon Bedrock model ID for commentary generation"
  type        = string
  default     = "anthropic.claude-3-haiku-20240307-v1:0"
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
  description = "Create the GitHub OIDC provider (only once per AWS account — set false in prod)"
  type        = bool
  default     = true
}

variable "cognito_domain_prefix" {
  description = "Globally unique prefix for the Cognito hosted UI domain"
  type        = string
}

variable "www_domain_name" {
  description = "Optional www domain (e.g. www.gameshift.live). Leave empty to skip www support."
  type        = string
  default     = ""
}

variable "allow_localhost" {
  description = "Add localhost OAuth redirect URLs to the Cognito client (dev only)"
  type        = bool
  default     = false
}
