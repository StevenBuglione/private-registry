terraform {
  required_version = ">= 1.10.0, < 2.0.0"

  backend "s3" {
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.80.0, < 7.0.0"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.6.0, < 4.0.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge({
      Product     = "private-registry"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }, var.tags)
  }
}

module "registry" {
  source = "../../modules/platform"

  project_name = "private-registry"
  environment  = var.environment
  aws_region   = var.aws_region
  tags         = var.tags

  vpc_cidr                = var.vpc_cidr
  availability_zone_count = 3
  create_nat_gateways     = var.create_nat_gateways
  single_nat_gateway      = var.single_nat_gateway
  transit_gateway_id      = var.transit_gateway_id
  corporate_route_cidrs   = var.corporate_route_cidrs
  corporate_ingress_cidrs = var.corporate_ingress_cidrs

  registry_dns_name = var.registry_dns_name
  route53_zone_id   = var.route53_zone_id
  certificate_arn   = var.certificate_arn

  oidc_client_id              = var.oidc_client_id
  oidc_client_secret          = var.oidc_client_secret
  oidc_issuer                 = var.oidc_issuer
  oidc_authorization_endpoint = var.oidc_authorization_endpoint
  oidc_token_endpoint         = var.oidc_token_endpoint
  oidc_user_info_endpoint     = var.oidc_user_info_endpoint
  oidc_scope                  = var.oidc_scope

  jfrog_hostname                   = var.jfrog_hostname
  jfrog_vpc_endpoint_service_name  = var.jfrog_vpc_endpoint_service_name
  jfrog_private_dns_enabled        = var.jfrog_private_dns_enabled
  jfrog_private_dns_zone_id        = var.jfrog_private_dns_zone_id
  jfrog_token_secret_arn           = var.jfrog_token_secret_arn
  jfrog_secret_kms_key_arn         = var.jfrog_secret_kms_key_arn
  authorization_secret_kms_key_arn = var.authorization_secret_kms_key_arn
  database_secret_kms_key_arn      = var.database_secret_kms_key_arn

  deploy_application_services = var.deploy_application_services
  ui_image_tag                = var.ui_image_tag
  api_image_tag               = var.api_image_tag
  migrations_image_tag        = var.migrations_image_tag
  ui_desired_count            = var.ui_desired_count
  api_desired_count           = var.api_desired_count
  ui_min_count                = var.ui_min_count
  ui_max_count                = var.ui_max_count
  api_min_count               = var.api_min_count
  api_max_count               = var.api_max_count

  aurora_engine_version            = var.aurora_engine_version
  aurora_instance_class            = var.aurora_instance_class
  aurora_instance_count            = var.aurora_instance_count
  database_deletion_protection     = var.database_deletion_protection
  database_skip_final_snapshot     = var.database_skip_final_snapshot
  database_proxy_secret_arn        = var.database_proxy_secret_arn
  enable_aurora_global_database    = var.enable_aurora_global_database
  aurora_global_cluster_identifier = var.aurora_global_cluster_identifier
  is_dr_region                     = var.is_dr_region
  primary_region                   = var.primary_region

  backup_copy_destination_vault_arn = var.backup_copy_destination_vault_arn
  alarm_notification_email          = var.alarm_notification_email
  load_balancer_deletion_protection = var.load_balancer_deletion_protection
  authorization_config_secret_arn   = var.authorization_config_secret_arn
  support_url                       = var.support_url

  create_github_oidc_provider        = var.create_github_oidc_provider
  github_oidc_provider_arn           = var.github_oidc_provider_arn
  github_owner                       = var.github_owner
  github_ui_repository               = var.github_ui_repository
  github_api_repository              = var.github_api_repository
  github_allowed_refs                = var.github_allowed_refs
  github_permissions_boundary_arn    = var.github_permissions_boundary_arn
  github_terraform_plan_policy_arns  = var.github_terraform_plan_policy_arns
  github_terraform_apply_policy_arns = var.github_terraform_apply_policy_arns
  github_image_policy_arns           = var.github_image_policy_arns
  github_migration_policy_arns       = var.github_migration_policy_arns
}
