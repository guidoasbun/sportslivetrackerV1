variable "project_name" {
  description = "Project name prefix for all resource names"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "domain_name" {
  description = "Full domain name for this environment — used to build OAuth callback URLs"
  type        = string
}

variable "cognito_domain_prefix" {
  description = "Prefix for the Cognito hosted UI domain — must be globally unique across all AWS accounts"
  type        = string
}

variable "google_client_id_arn" {
  description = "Secrets Manager ARN for the Google OAuth client ID"
  type        = string
}

variable "google_client_secret_arn" {
  description = "Secrets Manager ARN for the Google OAuth client secret"
  type        = string
}

variable "password_minimum_length" {
  description = "Minimum password length (applies if you ever add email/password sign-in)"
  type        = number
  default     = 8
}

variable "mfa_configuration" {
  description = "MFA enforcement: OFF, OPTIONAL, or ON"
  type        = string
  default     = "OFF"
}

variable "allow_localhost" {
  description = "When true, adds http://localhost:3000 callback and logout URLs for local development"
  type        = bool
  default     = false
}
