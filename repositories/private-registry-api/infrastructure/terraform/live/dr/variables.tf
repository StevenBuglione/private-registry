variable "aws_region" { type = string }
variable "environment" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}
variable "vpc_cidr" { type = string }
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
variable "database_deletion_protection" {
  type    = bool
  default = true
}
variable "database_skip_final_snapshot" {
  type    = bool
  default = false
}
variable "database_proxy_secret_arn" {
  type    = string
  default = null
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

variable "backup_copy_destination_vault_arn" {
  type    = string
  default = null
}
variable "alarm_notification_email" {
  type    = string
  default = null
}
variable "load_balancer_deletion_protection" {
  type    = bool
  default = true
}

variable "create_github_oidc_provider" {
  type    = bool
  default = false
}
variable "github_oidc_provider_arn" {
  type    = string
  default = null
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
