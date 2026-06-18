output "ecs_execution_role_arn" {
  description = "ARN of the shared ECS execution role (used by all ECS task definitions)"
  value       = aws_iam_role.ecs_execution.arn
}

output "producer_task_role_arn" {
  description = "ARN of the producer ECS task role"
  value       = aws_iam_role.producer_task.arn
}

output "lambda_execution_role_arn" {
  description = "ARN of the Lambda execution role"
  value       = aws_iam_role.lambda_execution.arn
}

output "frontend_task_role_arn" {
  description = "ARN of the frontend/API ECS task role"
  value       = aws_iam_role.frontend_task.arn
}

output "github_actions_role_arn" {
  description = "ARN of the GitHub Actions deploy role (paste into your GitHub Actions YAML)"
  value       = aws_iam_role.github_actions.arn
}