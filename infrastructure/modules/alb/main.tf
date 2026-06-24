# ──────────────────────────────────────────────────────────────
# Route 53 — look up the hosted zone you already own
# ──────────────────────────────────────────────────────────────
data "aws_route53_zone" "main" {
  name         = var.hosted_zone_name
  private_zone = false
}

# ──────────────────────────────────────────────────────────────
# ACM certificate
# AWS will issue the cert once it sees the validation DNS records
# below. Terraform creates those records automatically.
# ──────────────────────────────────────────────────────────────
resource "aws_acm_certificate" "main" {
  domain_name               = var.domain_name
  subject_alternative_names = var.www_domain_name != "" ? [var.www_domain_name] : []
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-cert"
    Environment = var.environment
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.main.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.main.zone_id
}

resource "aws_acm_certificate_validation" "main" {
  certificate_arn         = aws_acm_certificate.main.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}

# ──────────────────────────────────────────────────────────────
# Security group
# ALB accepts public traffic on 80 and 443. ECS containers only
# accept traffic from this SG — enforced in the ECS module.
# ──────────────────────────────────────────────────────────────
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-${var.environment}-alb-sg"
  description = "Allow inbound HTTP and HTTPS from the internet"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-alb-sg"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Application Load Balancer
# ──────────────────────────────────────────────────────────────
resource "aws_lb" "main" {
  name               = "${var.project_name}-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  tags = {
    Name        = "${var.project_name}-${var.environment}-alb"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Target group
# ECS containers register their IP here. The ALB health-checks
# each container before sending it live traffic.
# ──────────────────────────────────────────────────────────────
resource "aws_lb_target_group" "frontend" {
  name        = "${var.project_name}-${var.environment}-frontend-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = var.health_check_path
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-frontend-tg"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# API target group
# Spring Boot service on port 8080. Health-checked at /api/health.
# ──────────────────────────────────────────────────────────────
resource "aws_lb_target_group" "api" {
  name        = "${var.project_name}-${var.environment}-api-tg"
  port        = var.api_container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = var.api_health_check_path
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-api-tg"
    Environment = var.environment
  }
}

# ──────────────────────────────────────────────────────────────
# Listeners
# Port 80 permanently redirects to 443. Port 443 terminates TLS
# and forwards to ECS containers.
# ──────────────────────────────────────────────────────────────
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.main.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# ──────────────────────────────────────────────────────────────
# Listener rule — /api/auth/* → Frontend target group (Next.js Auth)
# Priority 5 wins over the generic /api/* rule below.
# ──────────────────────────────────────────────────────────────
resource "aws_lb_listener_rule" "frontend_auth_api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 5

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }

  condition {
    path_pattern {
      values = ["/api/auth/*"]
    }
  }
}

# ──────────────────────────────────────────────────────────────
# Listener rule — /api/* → API target group
# Priority 10 wins before the default forward-to-frontend action.
# ──────────────────────────────────────────────────────────────
resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

# ──────────────────────────────────────────────────────────────
# Route 53 alias record → ALB
# An alias (not a CNAME) is AWS-native: no extra DNS hop,
# no TTL cost, and it tracks ALB IP changes automatically.
# ──────────────────────────────────────────────────────────────
resource "aws_route53_record" "alb" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "www" {
  count   = var.www_domain_name != "" ? 1 : 0
  zone_id = data.aws_route53_zone.main.zone_id
  name    = var.www_domain_name
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}
