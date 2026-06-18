output "producer_repository_url" {
  description = "ECR repository URL for the producer service"
  value       = aws_ecr_repository.producer.repository_url
}

output "producer_repository_arn" {
  description = "ECR repository ARN for the producer service"
  value       = aws_ecr_repository.producer.arn
}

output "api_repository_url" {
  description = "ECR repository URL for the api service"
  value       = aws_ecr_repository.api.repository_url
}

output "api_repository_arn" {
  description = "ECR repository ARN for the api service"
  value       = aws_ecr_repository.api.arn
}

output "frontend_repository_url" {
  description = "ECR repository URL for the frontend service"
  value       = aws_ecr_repository.frontend.repository_url
}

output "frontend_repository_arn" {
  description = "ECR repository ARN for the frontend service"
  value       = aws_ecr_repository.frontend.arn
}
