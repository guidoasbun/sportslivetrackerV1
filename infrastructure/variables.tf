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

# ──────────────────────────────────────────────────────────────
# Scheduled scale-to-zero (cost control)
# ──────────────────────────────────────────────────────────────

variable "enable_scheduled_scaling" {
  description = "If true, ECS services scale to 0 tasks off-hours and back up on-hours on a cron schedule."
  type        = bool
  default     = false
}

variable "scale_up_cron" {
  description = "Application Auto Scaling cron for scaling services UP. Default: 08:00 weekdays."
  type        = string
  default     = "cron(0 8 ? * MON-FRI *)"
}

variable "scale_down_cron" {
  description = "Application Auto Scaling cron for scaling services DOWN to zero. Default: 20:00 daily."
  type        = string
  default     = "cron(0 20 * * ? *)"
}

variable "scaling_timezone" {
  description = "IANA timezone for the scaling cron expressions (e.g., America/New_York)."
  type        = string
  default     = "Etc/UTC"
}
