data "azuread_client_config" "current" {}

resource "azuread_group" "apm_developers" {
  for_each = var.apm_assignments

  display_name     = each.value.developerGroup
  description      = each.value.description
  security_enabled = true
  mail_enabled     = false
  owners           = [data.azuread_client_config.current.object_id]
  members          = each.value.entraMemberObjectIds
}

resource "platform_group" "apm_developers" {
  for_each = var.apm_assignments

  name                       = each.value.developerGroup
  description                = each.value.description
  external_id                = azuread_group.apm_developers[each.key].object_id
  admin_privileges           = false
  use_group_members_resource = true
}

# The trial does not yet have Entra group synchronization configured. Explicitly
# managing the current JFrog member makes the pilot enforceable now; external_id
# is ready for the identity-provider mapping when SSO provisioning is enabled.
resource "platform_group_members" "apm_developers" {
  for_each = var.apm_assignments

  name    = platform_group.apm_developers[each.key].name
  members = each.value.jfrogMembers
}

# Preserve general reader access to the non-APM repositories while removing the
# two Terraform mirrors from the former repository-wide "Anything" grant.
resource "platform_permission" "baseline_readers" {
  name = "Anything"

  artifact = {
    targets = [
      {
        name             = "docker-trial"
        include_patterns = ["**"]
      },
      {
        name             = "tf-trial"
        include_patterns = ["**"]
      }
    ]
    actions = {
      groups = [
        {
          name        = "readers"
          permissions = ["READ"]
        }
      ]
    }
  }

  build = {
    targets = [
      {
        name             = "artifactory-build-info"
        include_patterns = ["**"]
      }
    ]
    actions = {
      groups = [
        {
          name        = "readers"
          permissions = ["READ"]
        }
      ]
    }
  }
}

resource "platform_permission" "apm_developers" {
  for_each = var.apm_assignments

  name = each.value.developerGroup

  artifact = {
    targets = [
      {
        name             = artifactory_remote_terraform_repository.public_modules.key
        include_patterns = each.value.modulePatterns
      },
      {
        name             = artifactory_remote_terraform_repository.public_providers.key
        include_patterns = each.value.providerPatterns
      }
    ]
    actions = {
      groups = [
        {
          name        = platform_group.apm_developers[each.key].name
          permissions = ["READ"]
        }
      ]
    }
  }

  depends_on = [platform_group_members.apm_developers]
}
