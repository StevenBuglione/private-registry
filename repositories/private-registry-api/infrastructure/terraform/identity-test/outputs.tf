output "oidc" {
  description = "Non-secret OIDC endpoints and client identifier for local and ALB configuration."
  value = {
    tenant_id              = var.entra_tenant_id
    client_id              = azuread_application_registration.registry.client_id
    issuer                 = "https://login.microsoftonline.com/${var.entra_tenant_id}/v2.0"
    authorization_endpoint = "https://login.microsoftonline.com/${var.entra_tenant_id}/oauth2/v2.0/authorize"
    token_endpoint         = "https://login.microsoftonline.com/${var.entra_tenant_id}/oauth2/v2.0/token"
    user_info_endpoint     = "https://graph.microsoft.com/oidc/userinfo"
    scopes                 = "openid profile email offline_access User.Read"
  }
}

output "oidc_client_secret" {
  description = "Confidential OIDC client secret. Store only in an ignored local environment file or secret manager."
  value       = azuread_application_password.registry.value
  sensitive   = true
}

output "authorization_groups" {
  description = "Immutable Entra object IDs mapped to Registry APM and administrator roles."
  value = {
    APM9000001     = azuread_group.apm_a.object_id
    APM9000002     = azuread_group.apm_b.object_id
    registry_admin = azuread_group.registry_administrators.object_id
  }
}

output "test_users" {
  description = "Dedicated unlicensed acceptance-test users."
  value = {
    for key, user in azuread_user.test : key => {
      object_id           = user.object_id
      user_principal_name = user.user_principal_name
      password            = random_password.test_user[key].result
    }
  }
  sensitive = true
}
