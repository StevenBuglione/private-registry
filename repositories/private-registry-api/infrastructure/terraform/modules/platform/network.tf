resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = merge(local.common_tags, { Name = local.name })
}

resource "aws_internet_gateway" "this" {
  count  = var.create_nat_gateways ? 1 : 0
  vpc_id = aws_vpc.this.id
  tags   = merge(local.common_tags, { Name = "${local.name}-igw" })
}

resource "aws_subnet" "public" {
  for_each = { for index, az in local.azs : az => index }

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, each.value)
  map_public_ip_on_launch = false
  tags = merge(local.common_tags, {
    Name = "${local.name}-public-${each.key}"
    Tier = "public-egress"
  })
}

resource "aws_subnet" "application" {
  for_each = { for index, az in local.azs : az => index }

  vpc_id            = aws_vpc.this.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, each.value + 4)
  tags = merge(local.common_tags, {
    Name = "${local.name}-application-${each.key}"
    Tier = "application"
  })
}

resource "aws_subnet" "data" {
  for_each = { for index, az in local.azs : az => index }

  vpc_id            = aws_vpc.this.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, each.value + 8)
  tags = merge(local.common_tags, {
    Name = "${local.name}-data-${each.key}"
    Tier = "data"
  })
}

resource "aws_route_table" "public" {
  for_each = aws_subnet.public
  vpc_id   = aws_vpc.this.id
  tags     = merge(local.common_tags, { Name = "${local.name}-public-${each.key}" })
}

resource "aws_route" "public_internet" {
  for_each               = var.create_nat_gateways ? aws_route_table.public : {}
  route_table_id         = each.value.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this[0].id
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public[each.key].id
}

resource "aws_eip" "nat" {
  for_each = var.create_nat_gateways ? {
    for index, az in local.azs : az => index
    if !var.single_nat_gateway || index == 0
  } : {}
  domain     = "vpc"
  tags       = merge(local.common_tags, { Name = "${local.name}-nat-${each.key}" })
  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  for_each = aws_eip.nat

  allocation_id = each.value.id
  subnet_id     = aws_subnet.public[each.key].id
  tags          = merge(local.common_tags, { Name = "${local.name}-nat-${each.key}" })
  depends_on    = [aws_internet_gateway.this]
}

resource "aws_route_table" "application" {
  for_each = aws_subnet.application
  vpc_id   = aws_vpc.this.id
  tags     = merge(local.common_tags, { Name = "${local.name}-application-${each.key}" })
}

resource "aws_route" "application_nat" {
  for_each = var.create_nat_gateways ? aws_route_table.application : {}

  route_table_id         = each.value.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id = var.single_nat_gateway ? (
    aws_nat_gateway.this[local.azs[0]].id
  ) : aws_nat_gateway.this[each.key].id
}

resource "aws_route" "application_transit_default" {
  for_each = !var.create_nat_gateways && var.transit_gateway_id != null ? aws_route_table.application : {}

  route_table_id         = each.value.id
  destination_cidr_block = "0.0.0.0/0"
  transit_gateway_id     = var.transit_gateway_id
}

locals {
  application_corporate_routes = {
    for route in flatten([
      for az, route_table in aws_route_table.application : [
        for cidr in var.corporate_route_cidrs : {
          key            = "${az}-${replace(cidr, "/", "-")}"
          route_table_id = route_table.id
          cidr           = cidr
        }
      ]
    ]) : route.key => route
  }
}

resource "aws_route" "application_corporate" {
  for_each = var.transit_gateway_id == null ? {} : local.application_corporate_routes

  route_table_id         = each.value.route_table_id
  destination_cidr_block = each.value.cidr
  transit_gateway_id     = var.transit_gateway_id
}

resource "aws_route_table_association" "application" {
  for_each       = aws_subnet.application
  subnet_id      = each.value.id
  route_table_id = aws_route_table.application[each.key].id
}

resource "aws_route_table" "data" {
  for_each = aws_subnet.data
  vpc_id   = aws_vpc.this.id
  tags     = merge(local.common_tags, { Name = "${local.name}-data-${each.key}" })
}

resource "aws_route_table_association" "data" {
  for_each       = aws_subnet.data
  subnet_id      = each.value.id
  route_table_id = aws_route_table.data[each.key].id
}

resource "aws_flow_log" "vpc" {
  iam_role_arn             = aws_iam_role.vpc_flow_logs.arn
  log_destination          = aws_cloudwatch_log_group.vpc_flow_logs.arn
  traffic_type             = "ALL"
  vpc_id                   = aws_vpc.this.id
  max_aggregation_interval = 60
  tags                     = local.common_tags
}
