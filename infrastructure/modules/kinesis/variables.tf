variable "project_name" {
  description = "Project name prefix for all resources"
  type        = string
}

variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "shard_count" {
  description = "Deprecated: unused since the stream uses ON_DEMAND mode (no fixed shards). Retained for backward compatibility."
  type        = number
  default     = 1
}

variable "retention_period_hours" {
  description = "Number of hours to retain records in the stream"
  type        = number
  default     = 24
}
