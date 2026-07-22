resource "aws_lb" "this" {
  name                       = substr(local.name, 0, 32)
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = values(aws_subnet.application)[*].id
  enable_deletion_protection = var.load_balancer_deletion_protection
  drop_invalid_header_fields = true
  preserve_host_header       = true
  idle_timeout               = var.alb_idle_timeout_seconds

  access_logs {
    bucket  = aws_s3_bucket.this["alb_logs"].id
    prefix  = "alb"
    enabled = true
  }

  tags       = local.common_tags
  depends_on = [aws_s3_bucket_policy.alb_logs]
}

resource "aws_lb_target_group" "ui" {
  name        = substr("${local.name}-ui", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.this.id

  health_check {
    enabled             = true
    path                = "/healthz"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }

  deregistration_delay = 30
  tags                 = local.common_tags
}

resource "aws_lb_target_group" "api" {
  name        = substr("${local.name}-api", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.this.id

  health_check {
    enabled             = true
    path                = "/health/ready"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }

  deregistration_delay = 30
  tags                 = local.common_tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
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
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type  = "authenticate-oidc"
    order = 1
    authenticate_oidc {
      authorization_endpoint     = var.oidc_authorization_endpoint
      client_id                  = var.oidc_client_id
      client_secret              = var.oidc_client_secret
      issuer                     = var.oidc_issuer
      token_endpoint             = var.oidc_token_endpoint
      user_info_endpoint         = var.oidc_user_info_endpoint
      scope                      = var.oidc_scope
      session_cookie_name        = var.oidc_session_cookie_name
      session_timeout            = var.oidc_session_timeout
      on_unauthenticated_request = "authenticate"
    }
  }

  default_action {
    type             = "forward"
    order            = 2
    target_group_arn = aws_lb_target_group.ui.arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  action {
    type  = "authenticate-oidc"
    order = 1
    authenticate_oidc {
      authorization_endpoint     = var.oidc_authorization_endpoint
      client_id                  = var.oidc_client_id
      client_secret              = var.oidc_client_secret
      issuer                     = var.oidc_issuer
      token_endpoint             = var.oidc_token_endpoint
      user_info_endpoint         = var.oidc_user_info_endpoint
      scope                      = var.oidc_scope
      session_cookie_name        = var.oidc_session_cookie_name
      session_timeout            = var.oidc_session_timeout
      on_unauthenticated_request = "authenticate"
    }
  }

  action {
    type             = "forward"
    order            = 2
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    path_pattern { values = local.api_paths }
  }
}

resource "aws_route53_record" "registry" {
  count = var.route53_zone_id == null ? 0 : 1

  zone_id = var.route53_zone_id
  name    = var.registry_dns_name
  type    = "A"

  alias {
    name                   = aws_lb.this.dns_name
    zone_id                = aws_lb.this.zone_id
    evaluate_target_health = true
  }
}

resource "aws_wafv2_web_acl" "this" {
  count = var.enable_waf ? 1 : 0

  name  = local.name
  scope = "REGIONAL"
  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "common-rules"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 20
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "RateLimit"
    priority = 30
    action {
      block {}
    }
    statement {
      rate_based_statement {
        aggregate_key_type = "IP"
        limit              = var.waf_rate_limit
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "rate-limit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = local.name
    sampled_requests_enabled   = true
  }

  tags = local.common_tags
}

resource "aws_wafv2_web_acl_association" "this" {
  count = var.enable_waf ? 1 : 0

  resource_arn = aws_lb.this.arn
  web_acl_arn  = aws_wafv2_web_acl.this[0].arn
}
