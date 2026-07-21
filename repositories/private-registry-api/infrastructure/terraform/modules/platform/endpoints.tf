locals {
  interface_endpoint_services = toset([
    "ecr.api",
    "ecr.dkr",
    "logs",
    "monitoring",
    "secretsmanager",
    "kms",
    "sqs",
    "events",
    "sts",
    "ssmmessages",
    "xray"
  ])
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  vpc_endpoint_type = "Gateway"
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  route_table_ids   = concat(values(aws_route_table.application)[*].id, values(aws_route_table.data)[*].id)
  tags              = merge(local.common_tags, { Name = "${local.name}-s3" })
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoint_services

  vpc_id              = aws_vpc.this.id
  vpc_endpoint_type   = "Interface"
  service_name        = "com.amazonaws.${var.aws_region}.${each.value}"
  private_dns_enabled = true
  subnet_ids          = values(aws_subnet.application)[*].id
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.common_tags, { Name = "${local.name}-${replace(each.value, ".", "-")}" })
}

resource "aws_vpc_endpoint" "jfrog" {
  count = var.jfrog_vpc_endpoint_service_name == null ? 0 : 1

  vpc_id              = aws_vpc.this.id
  vpc_endpoint_type   = "Interface"
  service_name        = var.jfrog_vpc_endpoint_service_name
  private_dns_enabled = var.jfrog_private_dns_enabled
  subnet_ids          = values(aws_subnet.application)[*].id
  security_group_ids  = [aws_security_group.endpoints.id]
  tags                = merge(local.common_tags, { Name = "${local.name}-jfrog" })
}


resource "aws_route53_record" "jfrog_private_link" {
  count = (
    var.jfrog_vpc_endpoint_service_name != null &&
    !var.jfrog_private_dns_enabled &&
    var.jfrog_private_dns_zone_id != null
  ) ? 1 : 0

  zone_id = var.jfrog_private_dns_zone_id
  name    = var.jfrog_hostname
  type    = "A"

  alias {
    name                   = aws_vpc_endpoint.jfrog[0].dns_entry[0].dns_name
    zone_id                = aws_vpc_endpoint.jfrog[0].dns_entry[0].hosted_zone_id
    evaluate_target_health = false
  }
}
