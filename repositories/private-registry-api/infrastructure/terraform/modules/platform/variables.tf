variable "project_name" {
  type    = string
  default = "private-registry"
}
variable "environment" { type = string }
variable "aws_region" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}

variable "vpc_cidr" { type = string }
variable "availability_zone_count" {
  type    = number
  default = 3
}
variable "create_nat_gateways" {
  type    = bool
  default = true
}
variable "single_nat_gateway" {
  type    = bool
  default = false
}
variable "transit_gateway_id" {
  type    = string
  default = null
}
variable "corporate_route_cidrs" {
  type    = list(string)
  default = []
}
variable "corporate_ingress_cidrs" { type = list(string) }

variable "registry_dns_name" { type = string }
variable "route53_zone_id" {
  type    = string
  default = null
}
variable "certificate_arn" { type = string }

variable "oidc_client_id" {
  type      = string
  sensitive = true
}
variable "oidc_client_secret" {
  type      = string
  sensitive = true
}
variable "oidc_issuer" { type = string }
variable "oidc_authorization_endpoint" { type = string }
variable "oidc_token_endpoint" { type = string }
variable "oidc_user_info_endpoint" { type = string }
variable "oidc_scope" {
  type    = string
  default = "openid profile email"
}
variable "oidc_session_cookie_name" {
  type    = string
  default = "PrivateRegistryAuth"
}
variable "oidc_session_timeout" {
  type    = number
  default = 28800
}

variable "jfrog_hostname" { type = string }
variable "jfrog_vpc_endpoint_service_name" {
  type    = string
  default = null
}
variable "jfrog_private_dns_enabled" {
  type    = bool
  default = false
}
variable "jfrog_private_dns_zone_id" {
  type    = string
  default = null
}
variable "jfrog_token_secret_arn" {
  type    = string
  default = null
}
variable "jfrog_secret_kms_key_arn" {
  type    = string
  default = null
}
variable "jfrog_modules_release_repository" {
  type    = string
  default = "iac-modules-release-local"
}
variable "jfrog_providers_release_repository" {
  type    = string
  default = "iac-providers-release-local"
}
variable "jfrog_catalog_release_repository" {
  type    = string
  default = "iac-catalog-release-local"
}

variable "deploy_application_services" {
  type    = bool
  default = false
}
variable "ui_image_tag" {
  type    = string
  default = "not-deployed"
}
variable "api_image_tag" {
  type    = string
  default = "not-deployed"
}
variable "migrations_image_tag" {
  type    = string
  default = "not-deployed"
}
variable "ui_desired_count" {
  type    = number
  default = 3
}
variable "api_desired_count" {
  type    = number
  default = 3
}
variable "ui_min_count" {
  type    = number
  default = 3
}
variable "ui_max_count" {
  type    = number
  default = 20
}
variable "api_min_count" {
  type    = number
  default = 3
}
variable "api_max_count" {
  type    = number
  default = 30
}
variable "fargate_platform_version" {
  type    = string
  default = "LATEST"
}

variable "aurora_engine_version" {
  type    = string
  default = "16.6"
}
variable "aurora_instance_class" {
  type    = string
  default = "db.r7g.large"
}
variable "aurora_instance_count" {
  type    = number
  default = 3
}
variable "database_name" {
  type    = string
  default = "registry"
}
variable "database_master_username" {
  type    = string
  default = "registry_admin"
}
variable "database_backup_retention_days" {
  type    = number
  default = 35
}
variable "database_deletion_protection" {
  type    = bool
  default = true
}
variable "database_skip_final_snapshot" {
  type    = bool
  default = false
}
variable "database_performance_insights_retention_days" {
  type    = number
  default = 7
}
variable "database_monitoring_interval" {
  type    = number
  default = 60
}
variable "enable_aurora_global_database" {
  type    = bool
  default = false
}
variable "aurora_global_cluster_identifier" {
  type    = string
  default = null
}
variable "is_dr_region" {
  type    = bool
  default = false
}
variable "primary_region" {
  type    = string
  default = null
}

variable "alarm_notification_email" {
  type    = string
  default = null
}
variable "log_retention_days" {
  type    = number
  default = 90
}
variable "enable_waf" {
  type    = bool
  default = true
}
variable "waf_rate_limit" {
  type    = number
  default = 2000
}
variable "backup_copy_destination_vault_arn" {
  type    = string
  default = null
}
variable "backup_cold_storage_after_days" {
  type    = number
  default = 30
}
variable "backup_delete_after_days" {
  type    = number
  default = 365
}

variable "github_oidc_provider_arn" {
  type    = string
  default = null
}
variable "create_github_oidc_provider" {
  type    = bool
  default = false
}
variable "github_owner" {
  type    = string
  default = null
}
variable "github_ui_repository" {
  type    = string
  default = "private-registry-ui"
}
variable "github_api_repository" {
  type    = string
  default = "private-registry-api"
}
variable "github_allowed_refs" {
  type    = list(string)
  default = ["ref:refs/heads/main"]
}

variable "authorization_config_secret_arn" {
  type    = string
  default = null
}
variable "authorization_secret_kms_key_arn" {
  type    = string
  default = null
}
variable "database_secret_kms_key_arn" {
  type    = string
  default = null
}
variable "support_url" {
  type    = string
  default = ""
}
variable "enable_execute_command" {
  type    = bool
  default = false
}

variable "extra_task_environment" {
  type    = map(string)
  default = {}
}


variable "database_proxy_secret_arn" {
  description = "Secrets Manager secret used by RDS Proxy. In a global database, replicate an application database secret into each Region and supply its regional ARN."
  type        = string
  default     = null
}

variable "github_permissions_boundary_arn" {
  type    = string
  default = null
}

variable "github_terraform_plan_policy_arns" {
  type    = list(string)
  default = []
}

variable "github_terraform_apply_policy_arns" {
  type    = list(string)
  default = []
}

variable "github_image_policy_arns" {
  type    = list(string)
  default = []
}

variable "github_migration_policy_arns" {
  type    = list(string)
  default = []
}


variable "load_balancer_deletion_protection" {
  type    = bool
  default = true
}

variable "alb_idle_timeout_seconds" {
  type    = number
  default = 60
}

variable "ecs_deployment_minimum_healthy_percent" {
  type    = number
  default = 100
}

variable "ecs_deployment_maximum_percent" {
  type    = number
  default = 200
}
