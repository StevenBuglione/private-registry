data "azuread_client_config" "current" {}

data "azuread_domains" "default" {
  only_default = true
}

data "azuread_service_principal" "microsoft_graph" {
  client_id = "00000003-0000-0000-c000-000000000000"
}

locals {
  test_user_domain = coalesce(var.test_user_domain, data.azuread_domains.default.domains[0].domain_name)
  redirect_uris    = compact([var.local_redirect_uri, var.alb_redirect_uri])

  test_users = {
    apm_a = {
      display_name = "Registry E2E APM A"
      user_name    = "${var.test_user_prefix}-apm-a"
    }
    apm_b = {
      display_name = "Registry E2E APM B"
      user_name    = "${var.test_user_prefix}-apm-b"
    }
    admin = {
      display_name = "Registry E2E Administrator"
      user_name    = "${var.test_user_prefix}-admin"
    }
  }
}

resource "azuread_application_registration" "registry" {
  display_name                   = var.application_display_name
  description                    = "Single-tenant OIDC client for Registry local and ALB acceptance tests"
  sign_in_audience               = "AzureADMyOrg"
  requested_access_token_version = 2
  group_membership_claims        = ["None"]
  homepage_url                   = "http://localhost:3000/"
  logout_url                     = var.local_logout_uri
}

resource "azuread_application_redirect_uris" "registry" {
  application_id = azuread_application_registration.registry.id
  type           = "Web"
  redirect_uris  = local.redirect_uris
}

resource "azuread_application_api_access" "microsoft_graph" {
  application_id = azuread_application_registration.registry.id
  api_client_id  = data.azuread_service_principal.microsoft_graph.client_id
  scope_ids      = [data.azuread_service_principal.microsoft_graph.oauth2_permission_scope_ids["User.Read"]]
}

resource "azuread_service_principal" "registry" {
  client_id                    = azuread_application_registration.registry.client_id
  account_enabled              = true
  app_role_assignment_required = false
  owners                       = [data.azuread_client_config.current.object_id]
}

resource "azuread_service_principal_delegated_permission_grant" "registry_graph" {
  service_principal_object_id          = azuread_service_principal.registry.object_id
  resource_service_principal_object_id = data.azuread_service_principal.microsoft_graph.object_id
  claim_values                         = ["User.Read"]
}

resource "azuread_application_password" "registry" {
  application_id = azuread_application_registration.registry.id
  display_name   = "Registry automated acceptance"
  end_date       = "2028-07-21T00:00:00Z"
}

resource "random_password" "test_user" {
  for_each = local.test_users

  length           = 28
  special          = true
  override_special = "!@#%_-+"
}

resource "azuread_user" "test" {
  for_each = local.test_users

  user_principal_name         = "${each.value.user_name}@${local.test_user_domain}"
  display_name                = each.value.display_name
  mail_nickname               = each.value.user_name
  password                    = random_password.test_user[each.key].result
  account_enabled             = true
  force_password_change       = false
  disable_password_expiration = true
  usage_location              = "US"
}

resource "azuread_group" "apm_a" {
  display_name            = "APM9000001-developer"
  description             = "Registry automated acceptance group A"
  security_enabled        = true
  mail_enabled            = false
  prevent_duplicate_names = true
  owners                  = [data.azuread_client_config.current.object_id]
  members = [
    azuread_user.test["apm_a"].object_id,
    data.azuread_client_config.current.object_id,
  ]
}

resource "azuread_group" "apm_b" {
  display_name            = "APM9000002-developer"
  description             = "Registry automated acceptance group B"
  security_enabled        = true
  mail_enabled            = false
  prevent_duplicate_names = true
  owners                  = [data.azuread_client_config.current.object_id]
  members                 = [azuread_user.test["apm_b"].object_id]
}

resource "azuread_group" "registry_administrators" {
  display_name            = "Registry-Administrators-E2E"
  description             = "Registry automated acceptance administrator group"
  security_enabled        = true
  mail_enabled            = false
  prevent_duplicate_names = true
  owners                  = [data.azuread_client_config.current.object_id]
  members = [
    azuread_user.test["admin"].object_id,
    data.azuread_client_config.current.object_id,
  ]
}
