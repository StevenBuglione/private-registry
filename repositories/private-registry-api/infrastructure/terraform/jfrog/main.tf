locals {
  public_registry_url   = "https://registry.terraform.io"
  github_download_url   = "https://github.com/"
  hashicorp_release_url = "https://releases.hashicorp.com"
}

resource "artifactory_remote_terraform_repository" "public_modules" {
  key                     = "iac-modules-public-remote"
  description             = "Pull-through cache for approved public Terraform modules"
  notes                   = "Managed by the private-registry JFrog Terraform root"
  url                     = local.github_download_url
  terraform_registry_url  = local.public_registry_url
  terraform_providers_url = local.hashicorp_release_url

  repo_layout_ref         = "terraform-module-default"
  remote_repo_layout_ref  = "terraform-module-default"
  bypass_head_requests    = true
  store_artifacts_locally = true
  hard_fail               = true
  xray_index              = true

  lifecycle {
    prevent_destroy = true
  }
}

resource "artifactory_remote_terraform_repository" "public_providers" {
  key                     = "iac-providers-public-remote"
  description             = "Pull-through cache for approved public Terraform providers"
  notes                   = "Managed by the private-registry JFrog Terraform root"
  url                     = local.github_download_url
  terraform_registry_url  = local.public_registry_url
  terraform_providers_url = local.hashicorp_release_url

  repo_layout_ref         = "terraform-provider-default"
  remote_repo_layout_ref  = "terraform-provider-default"
  bypass_head_requests    = true
  store_artifacts_locally = true
  hard_fail               = true
  xray_index              = true

  lifecycle {
    prevent_destroy = true
  }
}
