output "user_pool_id" {
  description = "Cognito user pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  description = "Cognito user pool ARN"
  value       = aws_cognito_user_pool.main.arn
}

output "client_id" {
  description = "App client ID — set as COGNITO_CLIENT_ID in your NextAuth.js config"
  value       = aws_cognito_user_pool_client.frontend.id
}

output "client_secret" {
  description = "App client secret — set as COGNITO_CLIENT_SECRET in your NextAuth.js config"
  value       = aws_cognito_user_pool_client.frontend.client_secret
  sensitive   = true
}

output "cognito_domain" {
  description = "Full hosted UI base URL — used to build the authorization endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${data.aws_region.current.name}.amazoncognito.com"
}

output "issuer_url" {
  description = "OIDC issuer URL — set as COGNITO_ISSUER in your NextAuth.js config"
  value       = "https://cognito-idp.${data.aws_region.current.name}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}
