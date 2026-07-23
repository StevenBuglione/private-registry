module "label" {
  source  = "artifacts.example.invalid/iac-modules-public-remote__cloudposse/label/null"
  version = "0.25.0"

  namespace = "private-registry"
  stage     = "poc"
  name      = "mirror"
}

resource "null_resource" "mirror_proof" {
  triggers = {
    label = module.label.id
  }
}

output "mirror_proof_label" {
  description = "Label produced by the module resolved through Artifactory."
  value       = module.label.id
}
