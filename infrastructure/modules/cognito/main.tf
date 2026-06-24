data "aws_region" "current" {}

data "aws_secretsmanager_secret_version" "google_client_id" {
  secret_id = var.google_client_id_arn
}

data "aws_secretsmanager_secret_version" "google_client_secret" {
  secret_id = var.google_client_secret_arn
}

# ──────────────────────────────────────────────────────────────
# User Pool
# ──────────────────────────────────────────────────────────────
resource "aws_cognito_user_pool" "main" {
  name = "${var.project_name}-${var.environment}-user-pool"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]
  mfa_configuration        = var.mfa_configuration

  password_policy {
    minimum_length                   = var.password_minimum_length
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-user-pool"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Hosted UI domain
# Provides the /login, /oauth2/token, and /logout endpoints.
# Full URL: https://{prefix}.auth.{region}.amazoncognito.com
# ──────────────────────────────────────────────────────────────
resource "aws_cognito_user_pool_domain" "main" {
  domain       = var.cognito_domain_prefix
  user_pool_id = aws_cognito_user_pool.main.id
}

# ──────────────────────────────────────────────────────────────
# Google identity provider
# All provider_details fields are set explicitly to prevent
# Terraform from showing perpetual diffs — Cognito returns all
# of these in API responses even if you only supply three.
# ──────────────────────────────────────────────────────────────
resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.main.id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id                     = data.aws_secretsmanager_secret_version.google_client_id.secret_string
    client_secret                 = data.aws_secretsmanager_secret_version.google_client_secret.secret_string
    authorize_scopes              = "email profile openid"
    attributes_url                = "https://people.googleapis.com/v1/people/me?personFields="
    attributes_url_add_attributes = "true"
    authorize_url                 = "https://accounts.google.com/o/oauth2/v2/auth"
    oidc_issuer                   = "https://accounts.google.com"
    token_request_method          = "POST"
    token_url                     = "https://www.googleapis.com/oauth2/v4/token"
  }

  attribute_mapping = {
    email    = "email"
    username = "sub"
    name     = "name"
    picture  = "picture"
  }
}

# ──────────────────────────────────────────────────────────────
# App client for the Next.js frontend
# generate_secret = false because this is a public client communicating
# directly with Cognito, and a client secret cannot be securely stored in the browser.
# ──────────────────────────────────────────────────────────────
resource "aws_cognito_user_pool_client" "frontend" {
  name         = "${var.project_name}-${var.environment}-frontend-client"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  explicit_auth_flows = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_SRP_AUTH"]

  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["email", "openid", "profile"]
  allowed_oauth_flows_user_pool_client = true

  supported_identity_providers = ["Google"]

  callback_urls = concat(
    ["https://${var.domain_name}/api/auth/callback/cognito"],
    var.allow_localhost ? ["http://localhost:3000/api/auth/callback/cognito"] : []
  )
  logout_urls = concat(
    ["https://${var.domain_name}"],
    var.allow_localhost ? ["http://localhost:3000"] : []
  )

  depends_on = [aws_cognito_identity_provider.google]
}
