UPDATE registry_homepage_settings
SET featured_provider_ids = replace(
        featured_provider_ids,
        'provider/hashicorp/datadog',
        'provider/datadog/datadog')
WHERE updated_by = 'system'
  AND featured_provider_ids LIKE '%provider/hashicorp/datadog%';
