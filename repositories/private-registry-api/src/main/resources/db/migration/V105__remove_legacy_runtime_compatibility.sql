ALTER TABLE package_versions
    DROP COLUMN IF EXISTS opentofu_constraint;

COMMENT ON COLUMN package_versions.terraform_constraint IS
    'Terraform compatibility constraint for the governed package release.';
