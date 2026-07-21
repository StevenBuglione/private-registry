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
variable "bucket_replications" {
  type = map(object({
    source_bucket_id        = string
    source_bucket_arn       = string
    destination_bucket_arn  = string
    source_kms_key_arn      = string
    destination_kms_key_arn = string
  }))
  default = {}
}
variable "tags" {
  type    = map(string)
  default = {}
}
