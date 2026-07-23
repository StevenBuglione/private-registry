provider "aws" {
  alias  = "primary"
  region = var.primary_region
}
provider "aws" {
  alias  = "dr"
  region = var.dr_region
}

resource "aws_ecr_replication_configuration" "this" {
  provider = aws.primary
  replication_configuration {
    rule {
      destination {
        region      = var.dr_region
        registry_id = var.dr_account_id
      }
      repository_filter {
        filter      = var.ecr_repository_prefix
        filter_type = "PREFIX_MATCH"
      }
    }
  }
}

resource "aws_route53_record" "primary" {
  provider       = aws.primary
  zone_id        = var.route53_zone_id
  name           = var.registry_dns_name
  type           = "A"
  set_identifier = "primary"
  failover_routing_policy { type = "PRIMARY" }
  alias {
    name                   = var.primary_alb_dns_name
    zone_id                = var.primary_alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "dr" {
  provider       = aws.primary
  zone_id        = var.route53_zone_id
  name           = var.registry_dns_name
  type           = "A"
  set_identifier = "dr"
  failover_routing_policy { type = "SECONDARY" }
  alias {
    name                   = var.dr_alb_dns_name
    zone_id                = var.dr_alb_zone_id
    evaluate_target_health = true
  }
}
