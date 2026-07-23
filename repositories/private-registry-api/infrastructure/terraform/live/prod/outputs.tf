output "registry_url" { value = module.registry.registry_url }
output "ecs_cluster_name" { value = module.registry.ecs_cluster_name }
output "ecr_repository_urls" { value = module.registry.ecr_repository_urls }
output "database_cluster_arn" { value = module.registry.database_cluster_arn }
output "database_proxy_endpoint" { value = module.registry.database_proxy_endpoint }
output "github_role_arns" { value = module.registry.github_role_arns }
output "task_definition_arns" { value = module.registry.task_definition_arns }
