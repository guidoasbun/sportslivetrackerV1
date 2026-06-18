output "alb_arn" {
  description = "ARN of the Application Load Balancer"
  value       = aws_lb.main.arn
}

output "alb_dns_name" {
  description = "DNS name of the ALB (used to verify the load balancer is reachable)"
  value       = aws_lb.main.dns_name
}

output "alb_zone_id" {
  description = "Route 53 zone ID of the ALB (needed to create alias records for additional subdomains)"
  value       = aws_lb.main.zone_id
}

output "security_group_id" {
  description = "Security group ID of the ALB — ECS containers allow inbound only from this SG"
  value       = aws_security_group.alb.id
}

output "target_group_arn" {
  description = "ARN of the frontend target group — passed to the ECS service so containers register here"
  value       = aws_lb_target_group.frontend.arn
}

output "https_listener_arn" {
  description = "ARN of the HTTPS listener — needed if you add additional listener rules later"
  value       = aws_lb_listener.https.arn
}

output "domain_name" {
  description = "The domain name this ALB is serving (e.g. dev.gameshift.live)"
  value       = var.domain_name
}

output "api_target_group_arn" {
  description = "ARN of the API target group — passed to the ECS service so containers register here"
  value       = aws_lb_target_group.api.arn
}
