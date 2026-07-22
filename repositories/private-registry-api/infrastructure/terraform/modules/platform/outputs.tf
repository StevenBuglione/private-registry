output "vpc_id" { value = aws_vpc.this.id }
output "application_subnet_ids" { value = values(aws_subnet.application)[*].id }
output "data_subnet_ids" { value = values(aws_subnet.data)[*].id }
output "service_security_group_id" { value = aws_security_group.services.id }
output "load_balancer_dns_name" { value = aws_lb.this.dns_name }
output "load_balancer_zone_id" { value = aws_lb.this.zone_id }
output "registry_url" { value = "https://${var.registry_dns_name}" }
output "ecs_cluster_name" { value = aws_ecs_cluster.this.name }
output "ecs_cluster_arn" { value = aws_ecs_cluster.this.arn }
output "ecr_repository_urls" {
  value = { for key, repository in aws_ecr_repository.service :
  key => repository.repository_url }
}
output "database_cluster_arn" { value = local.selected_cluster_arn }
output "database_proxy_endpoint" { value = aws_db_proxy.this.endpoint }
output "database_master_secret_arn" {
  value     = local.selected_master_secret_arn
  sensitive = true
}
output "opensearch_endpoint" { value = aws_opensearch_domain.this.endpoint }
output "documentation_bucket" { value = aws_s3_bucket.this["documentation"].id }
output "quarantine_bucket" { value = aws_s3_bucket.this["quarantine"].id }
output "audit_bucket" { value = aws_s3_bucket.this["audit"].id }
output "reconciliation_bucket" { value = aws_s3_bucket.this["reconciliation"].id }
output "ingestion_queue_url" { value = aws_sqs_queue.ingestion.url }
output "ingestion_queue_arn" { value = aws_sqs_queue.ingestion.arn }
output "ingestion_dlq_url" { value = aws_sqs_queue.dlq.url }
output "event_bus_name" { value = aws_cloudwatch_event_bus.catalog.name }
output "event_bus_arn" { value = aws_cloudwatch_event_bus.catalog.arn }
output "alarm_topic_arn" { value = aws_sns_topic.alarms.arn }
output "jfrog_vpc_endpoint_id" { value = try(aws_vpc_endpoint.jfrog[0].id, null) }
output "github_role_arns" {
  value = { for key, role in aws_iam_role.github :
  key => role.arn }
}
output "task_definition_arns" {
  value = var.deploy_application_services ? {
    ui         = aws_ecs_task_definition.ui[0].arn
    api        = aws_ecs_task_definition.api[0].arn
    indexer    = aws_ecs_task_definition.indexer[0].arn
    reconciler = aws_ecs_task_definition.reconciler[0].arn
    migrations = aws_ecs_task_definition.migrations[0].arn
  } : {}
}
