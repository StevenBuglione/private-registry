variable "jfrog_hostname" {
  description = "JFrog platform hostname without a URL scheme."
  type        = string
  validation {
    condition     = can(regex("^[A-Za-z0-9.-]+$", var.jfrog_hostname))
    error_message = "jfrog_hostname must be a hostname without a scheme or path."
  }
}

variable "entra_tenant_id" {
  description = "Microsoft Entra tenant that owns the APM developer groups."
  type        = string
}

variable "apm_assignments" {
  description = "APM package assignments and the Entra/JFrog members authorized for each package set."
  type = map(object({
    developerGroup       = string
    description          = string
    entraMemberObjectIds = list(string)
    jfrogMembers         = list(string)
    modulePatterns       = list(string)
    providerPatterns     = list(string)
    propertyTargets      = list(string)
  }))
}
