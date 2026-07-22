variable "entra_tenant_id" {
  description = "Microsoft Entra tenant used for Registry OIDC acceptance testing."
  type        = string
}

variable "application_display_name" {
  description = "Display name for the single-tenant Registry test application."
  type        = string
  default     = "Registry Local Acceptance"
}

variable "local_redirect_uri" {
  description = "Spring Security OAuth callback routed through the local Registry UI."
  type        = string
  default     = "http://localhost:3000/login/oauth2/code/entra"
}

variable "local_logout_uri" {
  description = "Local signed-out landing page."
  type        = string
  default     = "http://localhost:3000/signed-out"
}

variable "alb_redirect_uri" {
  description = "Optional production ALB OIDC callback URI. Leave null for local-only acceptance."
  type        = string
  default     = null
  nullable    = true
}

variable "test_user_domain" {
  description = "Optional verified tenant domain for the test users; the tenant default domain is used when null."
  type        = string
  default     = null
  nullable    = true
}

variable "test_user_prefix" {
  description = "Collision-resistant prefix for unlicensed cloud-only acceptance users."
  type        = string
  default     = "registry-e2e"

  validation {
    condition     = can(regex("^[a-z0-9-]{3,30}$", var.test_user_prefix))
    error_message = "test_user_prefix must contain 3-30 lowercase letters, digits, or hyphens."
  }
}
