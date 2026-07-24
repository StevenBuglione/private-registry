data "aws_availability_zones" "available" { state = "available" }
data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
data "aws_region" "current" {}

locals {
  name = "${var.project_name}-${var.environment}"
  azs  = slice(data.aws_availability_zones.available.names, 0, var.availability_zone_count)

  common_tags = merge({
    ManagedBy   = "Terraform"
    Product     = var.project_name
    Environment = var.environment
  }, var.tags)

  repository_names = {
    ui         = "${local.name}-ui"
    api        = "${local.name}-api"
    migrations = "${local.name}-migrations"
  }

  api_paths = ["/api/v1/*", "/swagger-ui*", "/oauth2/*", "/login/*", "/logout"]

  selected_cluster_arn       = var.is_dr_region ? try(aws_rds_cluster.secondary[0].arn, null) : try(aws_rds_cluster.primary[0].arn, null)
  selected_cluster_id        = var.is_dr_region ? try(aws_rds_cluster.secondary[0].id, null) : try(aws_rds_cluster.primary[0].id, null)
  selected_cluster_endpoint  = var.is_dr_region ? try(aws_rds_cluster.secondary[0].endpoint, "") : try(aws_rds_cluster.primary[0].endpoint, "")
  selected_master_secret_arn = var.is_dr_region ? try(aws_rds_cluster.secondary[0].master_user_secret[0].secret_arn, null) : try(aws_rds_cluster.primary[0].master_user_secret[0].secret_arn, null)
}


locals {
  database_proxy_secret_arn = var.database_proxy_secret_arn != null ? var.database_proxy_secret_arn : local.selected_master_secret_arn
}
