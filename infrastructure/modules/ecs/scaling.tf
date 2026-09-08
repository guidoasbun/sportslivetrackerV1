# ──────────────────────────────────────────────────────────────
# Scheduled scale-to-zero for the ECS services
#
# Uses Application Auto Scaling to flip each service between 0 tasks
# (off-hours) and its desired count (on-hours) on a cron schedule.
# The ALB, NAT gateway, Kinesis stream, and DynamoDB tables stay up;
# only the Fargate tasks (the bulk of the runtime cost) scale to zero.
#
# All resources are gated on var.enable_scheduled_scaling so the schedule
# can be toggled per environment without removing the wiring.
# ──────────────────────────────────────────────────────────────

locals {
  # Map of logical service key -> the values Application Auto Scaling needs.
  # min_capacity holds the on-hours desired count for each service.
  scalable_services = {
    producer = {
      service_name = aws_ecs_service.producer.name
      max_capacity = var.producer_desired_count
    }
    api = {
      service_name = aws_ecs_service.api.name
      max_capacity = var.api_desired_count
    }
    frontend = {
      service_name = aws_ecs_service.frontend.name
      max_capacity = var.frontend_desired_count
    }
  }

  # Only build the maps when scaling is enabled.
  scaling_targets = var.enable_scheduled_scaling ? local.scalable_services : {}
}

# Register each service as a scalable target on the DesiredCount dimension.
resource "aws_appautoscaling_target" "ecs" {
  for_each = local.scaling_targets

  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${each.value.service_name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = 0
  max_capacity       = each.value.max_capacity
}

# Scale DOWN to zero at the configured time.
resource "aws_appautoscaling_scheduled_action" "scale_down" {
  for_each = local.scaling_targets

  name               = "${var.project_name}-${var.environment}-${each.key}-scale-down"
  service_namespace  = aws_appautoscaling_target.ecs[each.key].service_namespace
  resource_id        = aws_appautoscaling_target.ecs[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[each.key].scalable_dimension

  schedule = var.scale_down_cron
  timezone = var.scaling_timezone

  scalable_target_action {
    min_capacity = 0
    max_capacity = 0
  }
}

# Scale UP to the service's desired count at the configured time.
resource "aws_appautoscaling_scheduled_action" "scale_up" {
  for_each = local.scaling_targets

  name               = "${var.project_name}-${var.environment}-${each.key}-scale-up"
  service_namespace  = aws_appautoscaling_target.ecs[each.key].service_namespace
  resource_id        = aws_appautoscaling_target.ecs[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[each.key].scalable_dimension

  schedule = var.scale_up_cron
  timezone = var.scaling_timezone

  scalable_target_action {
    min_capacity = each.value.max_capacity
    max_capacity = each.value.max_capacity
  }
}
