output "cluster_name" {
  description = "Name of the ECS cluster"
  value       = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  description = "ARN of the ECS cluster"
  value       = aws_ecs_cluster.main.arn
}

output "producer_service_name" {
  description = "Name of the producer ECS service (used by CI/CD to trigger deployments)"
  value       = aws_ecs_service.producer.name
}

output "frontend_service_name" {
  description = "Name of the frontend ECS service (used by CI/CD to trigger deployments)"
  value       = aws_ecs_service.frontend.name
}

output "ecs_tasks_security_group_id" {
  description = "Security group ID attached to all ECS tasks"
  value       = aws_security_group.ecs_tasks.id
}

output "api_service_name" {
  description = "Name of the API ECS service (used by CI/CD to trigger deployments)"
  value       = aws_ecs_service.api.name
}
