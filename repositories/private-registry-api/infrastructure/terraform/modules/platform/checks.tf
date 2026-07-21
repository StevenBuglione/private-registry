check "three_availability_zones" {
  assert {
    condition     = var.availability_zone_count == 3
    error_message = "The reference architecture requires exactly three Availability Zones. Create a separate lower-resilience module variant rather than silently reducing this platform."
  }
}

check "opensearch_standby_topology" {
  assert {
    condition     = var.opensearch_zone_awareness_count == 3 && var.opensearch_data_instance_count >= 3 && var.opensearch_data_instance_count % 3 == 0
    error_message = "OpenSearch Multi-AZ with Standby requires three zones and a data-node count divisible by three."
  }
}

check "production_capacity" {
  assert {
    condition = var.environment != "prod" || (
      var.aurora_instance_count >= 3 &&
      var.ui_desired_count >= 3 &&
      var.api_desired_count >= 3 &&
      var.indexer_desired_count >= 2
    )
    error_message = "Production requires at least three Aurora instances, three UI tasks, three API tasks, and two indexer tasks."
  }
}

check "immutable_image_tags" {
  assert {
    condition = !var.deploy_application_services || alltrue([
      for tag in [
        var.ui_image_tag,
        var.api_image_tag,
        var.indexer_image_tag,
        var.reconciler_image_tag,
        var.migrations_image_tag
      ] : length(trimspace(tag)) >= 7 && tag != "not-deployed"
    ])
    error_message = "Application deployment requires immutable non-placeholder image tags, normally Git commit SHAs."
  }
}

check "audit_bucket_safety" {
  assert {
    condition     = !(var.audit_object_lock_enabled && var.force_destroy_buckets)
    error_message = "Object Lock and force_destroy_buckets cannot be enabled together."
  }
}

check "dr_database_configuration" {
  assert {
    condition = !var.is_dr_region || (
      var.enable_aurora_global_database &&
      var.aurora_global_cluster_identifier != null &&
      var.primary_region != null &&
      var.database_proxy_secret_arn != null
    )
    error_message = "The DR Region requires the Aurora global identifier, primary Region, and a replicated regional RDS Proxy secret."
  }
}

check "github_oidc_inputs" {
  assert {
    condition = var.github_owner == null || (
      (var.create_github_oidc_provider || var.github_oidc_provider_arn != null) &&
      length(var.github_ui_repository) > 0 &&
      length(var.github_api_repository) > 0
    )
    error_message = "GitHub OIDC roles require a GitHub owner, both repository names, and an OIDC provider ARN or provider creation."
  }
}
