variable "project_name" {
  description = "Project name prefix for all resource names"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev or prod)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the ALB will be created"
  type        = string
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs for the ALB (needs at least 2 AZs)"
  type        = list(string)
}

variable "domain_name" {
  description = "Full domain name for this environment (e.g. dev.gameshift.live or gameshift.live)"
  type        = string
}

variable "hosted_zone_name" {
  description = "Root Route 53 hosted zone name (the domain you own)"
  type        = string
  default     = "gameshift.live"
}

variable "container_port" {
  description = "Port the frontend container listens on"
  type        = number
  default     = 3000
}

variable "health_check_path" {
  description = "HTTP path the ALB uses to health-check ECS containers"
  type        = string
  default     = "/api/health"
}

variable "www_domain_name" {
  description = "Optional www domain (e.g. www.gameshift.live). Leave empty to skip www support."
  type        = string
  default     = ""
}

variable "api_container_port" {
  description = "Port the API container listens on (Spring Boot default)"
  type        = number
  default     = 8080
}

variable "api_health_check_path" {
  description = "HTTP path the ALB uses to health-check the API containers"
  type        = string
  default     = "/api/health"
}
