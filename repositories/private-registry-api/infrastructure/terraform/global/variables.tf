variable "primary_region" { type = string }
variable "dr_region" { type = string }
variable "dr_account_id" { type = string }
variable "route53_zone_id" { type = string }
variable "registry_dns_name" { type = string }
variable "primary_alb_dns_name" { type = string }
variable "primary_alb_zone_id" { type = string }
variable "dr_alb_dns_name" { type = string }
variable "dr_alb_zone_id" { type = string }
variable "ecr_repository_prefix" {
  type    = string
  default = "private-registry"
}
variable "tags" {
  type    = map(string)
  default = {}
}
