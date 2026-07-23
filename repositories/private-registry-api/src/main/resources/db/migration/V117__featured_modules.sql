ALTER TABLE registry_homepage_settings
    ADD COLUMN featured_module_ids text NOT NULL DEFAULT
        'module/terraform-module/release/helm,module/terraform-aws-modules/iam/aws,module/terraform-google-modules/project-factory/google,module/terraform-google-modules/network/google,module/terraform-google-modules/kubernetes-engine/google,module/Azure/avm-res-web-site/azurerm';

COMMENT ON COLUMN registry_homepage_settings.featured_module_ids IS
    'Ordered public module IDs selected by administrators for homepage promotion.';
