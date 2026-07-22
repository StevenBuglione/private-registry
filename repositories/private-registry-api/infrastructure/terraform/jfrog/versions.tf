terraform {
  required_version = ">= 1.11, < 2.0"

  required_providers {
    artifactory = {
      source  = "jfrog/artifactory"
      version = "12.11.8"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "3.9.0"
    }
    platform = {
      source  = "jfrog/platform"
      version = "2.2.10"
    }
  }

  backend "azurerm" {}
}

provider "artifactory" {}

provider "azuread" {
  tenant_id = var.entra_tenant_id
}

provider "platform" {
  url = "https://${var.jfrog_hostname}"
}
