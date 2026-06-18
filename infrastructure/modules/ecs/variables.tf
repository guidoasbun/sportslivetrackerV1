variable "project_name" {
  description = "Project name prefix for all resource names"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where ECS tasks will run"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs where ECS tasks are placed (no direct internet access)"
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "Security group ID of the ALB — ECS tasks only accept traffic from it"
  type        = string
}

variable "ecs_execution_role_arn" {
  description = "ARN of the shared ECS execution role (ECR pull, Secrets Manager)"
  type        = string
}

variable "producer_task_role_arn" {
  description = "ARN of the producer task role (Kinesis write)"
  type        = string
}

variable "frontend_task_role_arn" {
  description = "ARN of the frontend task role (DynamoDB read)"
  type        = string
}

variable "target_group_arn" {
  description = "ARN of the ALB target group the frontend service registers into"
  type        = string
}

variable "ecr_producer_repository_url" {
  description = "ECR repository URL for the producer image"
  type        = string
}

variable "ecr_frontend_repository_url" {
  description = "ECR repository URL for the frontend image"
  type        = string
}

variable "kinesis_stream_name" {
  description = "Kinesis stream name passed to the producer as an environment variable"
  type        = string
}

variable "api_sports_key_arn" {
  description = "Secrets Manager ARN for the API-Sports key (injected into the producer container)"
  type        = string
}

variable "producer_cpu" {
  description = "CPU units for the producer task (1024 = 1 vCPU)"
  type        = number
  default     = 256
}

variable "producer_memory" {
  description = "Memory in MB for the producer task"
  type        = number
  default     = 512
}

variable "frontend_cpu" {
  description = "CPU units for the frontend task"
  type        = number
  default     = 256
}

variable "frontend_memory" {
  description = "Memory in MB for the frontend task"
  type        = number
  default     = 512
}

variable "producer_desired_count" {
  description = "Number of producer container instances to run"
  type        = number
  default     = 1
}

variable "frontend_desired_count" {
  description = "Number of frontend container instances to run"
  type        = number
  default     = 1
}

variable "container_port" {
  description = "Port the frontend container listens on (must match the ALB target group)"
  type        = number
  default     = 3000
}

variable "api_task_role_arn" {
  description = "ARN of the API task role (DynamoDB read)"
  type        = string
}

variable "ecr_api_repository_url" {
  description = "ECR repository URL for the API image"
  type        = string
}

variable "api_target_group_arn" {
  description = "ARN of the ALB target group the API service registers into"
  type        = string
}

variable "api_container_port" {
  description = "Port the API container listens on (Spring Boot default)"
  type        = number
  default     = 8080
}

variable "api_cpu" {
  description = "CPU units for the API task (1024 = 1 vCPU)"
  type        = number
  default     = 256
}

variable "api_memory" {
  description = "Memory in MB for the API task"
  type        = number
  default     = 512
}

variable "api_desired_count" {
  description = "Number of API container instances to run"
  type        = number
  default     = 1
}

variable "events_table_name" {
  description = "DynamoDB Events table name, passed to the API container as an environment variable"
  type        = string
}

variable "summaries_table_name" {
  description = "DynamoDB Summaries table name, passed to the API container as an environment variable"
  type        = string
}
