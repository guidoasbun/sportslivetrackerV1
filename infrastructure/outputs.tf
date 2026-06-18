output "alb_domain_name" {
  description = "Public URL where the app is reachable"
  value       = module.alb.domain_name
}

output "ecr_producer_repository_url" {
  description = "ECR URL to push the producer Docker image to"
  value       = module.ecr.producer_repository_url
}

output "ecr_api_repository_url" {
  description = "ECR URL to push the API Docker image to"
  value       = module.ecr.api_repository_url
}

output "ecr_frontend_repository_url" {
  description = "ECR URL to push the frontend Docker image to"
  value       = module.ecr.frontend_repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name — used by GitHub Actions to trigger deployments"
  value       = module.ecs.cluster_name
}

output "producer_service_name" {
  description = "ECS service name for the producer — used by GitHub Actions to force new deployments"
  value       = module.ecs.producer_service_name
}

output "frontend_service_name" {
  description = "ECS service name for the frontend — used by GitHub Actions to force new deployments"
  value       = module.ecs.frontend_service_name
}

output "api_service_name" {
  description = "ECS service name for the API — used by GitHub Actions to force new deployments"
  value       = module.ecs.api_service_name
}

output "lambda_function_name" {
  description = "Lambda function name — used by GitHub Actions to upload new JAR deployments"
  value       = module.lambda.function_name
}

output "github_actions_role_arn" {
  description = "IAM role ARN that GitHub Actions assumes via OIDC — add this to your GitHub repo secrets"
  value       = module.iam.github_actions_role_arn
}

output "cognito_user_pool_id" {
  description = "Cognito User Pool ID — needed by the frontend auth configuration"
  value       = module.cognito.user_pool_id
}

output "cognito_client_id" {
  description = "Cognito App Client ID — needed by the frontend auth configuration"
  value       = module.cognito.client_id
}

output "cognito_domain" {
  description = "Cognito hosted UI domain — the OAuth authorize/token endpoint base URL"
  value       = module.cognito.cognito_domain
}

output "kinesis_stream_name" {
  description = "Kinesis stream name — useful for manual testing and monitoring"
  value       = module.kinesis.stream_name
}
