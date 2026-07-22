output "module_repository_key" {
  description = "Artifactory repository key used for public Terraform modules."
  value       = artifactory_remote_terraform_repository.public_modules.key
}

output "module_source_prefix" {
  description = "Prefix used when constructing module registry source addresses."
  value       = "${var.jfrog_hostname}/${artifactory_remote_terraform_repository.public_modules.key}__"
}

output "provider_repository_key" {
  description = "Artifactory repository key used for public Terraform providers."
  value       = artifactory_remote_terraform_repository.public_providers.key
}

output "provider_mirror_endpoint" {
  description = "Terraform provider network mirror endpoint."
  value       = "https://${var.jfrog_hostname}/artifactory/api/terraform/${artifactory_remote_terraform_repository.public_providers.key}/providers/"
}

output "apm_access_groups" {
  description = "APM IDs mapped to their Entra and JFrog developer groups."
  value = {
    for apm_id, group in azuread_group.apm_developers : apm_id => {
      name            = group.display_name
      entra_object_id = group.object_id
      jfrog_group     = platform_group.apm_developers[apm_id].name
    }
  }
}
