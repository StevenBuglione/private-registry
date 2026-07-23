resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  configuration {
    execute_command_configuration {
      kms_key_id = aws_kms_key.data.arn
      logging    = "OVERRIDE"
      log_configuration {
        cloud_watch_encryption_enabled = true
        cloud_watch_log_group_name     = aws_cloudwatch_log_group.ecs_exec.name
      }
    }
  }

  tags = local.common_tags
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }
}

locals {
  service_log_groups = {
    ui         = "/ecs/${local.name}/ui"
    api        = "/ecs/${local.name}/api"
    migrations = "/ecs/${local.name}/migrations"
  }

  common_service_environment = merge({
    REGISTRY_ENVIRONMENT                     = var.environment
    REGISTRY_LOG_LEVEL                       = "info"
    REGISTRY_DATABASE_HOST                   = aws_db_proxy.this.endpoint
    REGISTRY_DATABASE_PORT                   = "5432"
    REGISTRY_DATABASE_NAME                   = var.database_name
    REGISTRY_DATABASE_USER                   = "registry_app"
    REGISTRY_DATABASE_SECRET_ARN             = local.database_proxy_secret_arn
    REGISTRY_JFROG_BASE_URL                  = "https://${var.jfrog_hostname}"
    REGISTRY_JFROG_TOKEN_SECRET_ARN          = var.jfrog_token_secret_arn == null ? "" : var.jfrog_token_secret_arn
    REGISTRY_EVENTING_ENABLED                = "true"
    REGISTRY_INGESTION_ENABLED               = "true"
    REGISTRY_RECONCILE_ON_STARTUP            = "false"
    REGISTRY_ALLOWED_ALB_SIGNER_ARN          = aws_lb.this.arn
    REGISTRY_ALLOWED_OIDC_ISSUER             = var.oidc_issuer
    REGISTRY_AUTHORIZATION_CONFIG_SECRET_ARN = var.authorization_config_secret_arn == null ? "" : var.authorization_config_secret_arn
    SPRING_PROFILES_ACTIVE                   = ""
    REGISTRY_SECURITY_PERMIT_ALL             = "false"
  }, var.extra_task_environment)

  ui_environment = {
    REGISTRY_DATA_API_URL       = "/registry/docs/"
    REGISTRY_ENTERPRISE_API_URL = "/api/v1/enterprise"
    REGISTRY_JFROG_HOSTNAME     = var.jfrog_hostname
    REGISTRY_ENVIRONMENT        = var.environment
    REGISTRY_SUPPORT_URL        = var.support_url
    REGISTRY_FEATURE_PROVIDERS  = "true"
    REGISTRY_FEATURE_MODULES    = "true"
    REGISTRY_FEATURE_SECURITY   = "true"
    REGISTRY_FEATURE_AUDIT      = "false"
  }
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = local.service_log_groups

  name              = each.value
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "ecs_exec" {
  name              = "/ecs/${local.name}/execute-command"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.logs.arn
  tags              = local.common_tags
}

resource "aws_ecs_task_definition" "ui" {
  count = var.deploy_application_services ? 1 : 0

  family                   = "${local.name}-ui"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.ui_task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  volume { name = "nginx-cache" }
  volume { name = "nginx-run" }
  volume { name = "nginx-tmp" }
  volume { name = "runtime-config" }

  container_definitions = jsonencode([{
    name         = "registry-web"
    image        = "${aws_ecr_repository.service["ui"].repository_url}:${var.ui_image_tag}"
    essential    = true
    portMappings = [{ containerPort = 8080, hostPort = 8080, protocol = "tcp", name = "http" }]
    environment  = [for key, value in local.ui_environment : { name = key, value = value }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service["ui"].name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ui"
      }
    }
    mountPoints = [
      { sourceVolume = "nginx-cache", containerPath = "/var/cache/nginx", readOnly = false },
      { sourceVolume = "nginx-run", containerPath = "/var/run", readOnly = false },
      { sourceVolume = "nginx-tmp", containerPath = "/tmp", readOnly = false },
      { sourceVolume = "runtime-config", containerPath = "/usr/share/nginx/html/config", readOnly = false }
    ]
    linuxParameters        = { initProcessEnabled = true }
    readonlyRootFilesystem = true
    stopTimeout            = 30
  }])

  tags = local.common_tags
}

resource "aws_ecs_task_definition" "api" {
  count = var.deploy_application_services ? 1 : 0

  family                   = "${local.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.api_task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name         = "catalog-api"
    image        = "${aws_ecr_repository.service["api"].repository_url}:${var.api_image_tag}"
    essential    = true
    portMappings = [{ containerPort = 8080, hostPort = 8080, protocol = "tcp", name = "http" }]
    environment = [for key, value in merge(local.common_service_environment, {
      REGISTRY_HTTP_ADDRESS  = ":8080"
      REGISTRY_DATABASE_USER = "registry_app"
    }) : { name = key, value = value }]
    logConfiguration = {
      logDriver = "awslogs"
      options   = { awslogs-group = aws_cloudwatch_log_group.service["api"].name, awslogs-region = var.aws_region, awslogs-stream-prefix = "api" }
    }
    linuxParameters        = { initProcessEnabled = true }
    readonlyRootFilesystem = true
    stopTimeout            = 30
  }])

  tags = local.common_tags
}

resource "aws_ecs_task_definition" "migrations" {
  count = var.deploy_application_services ? 1 : 0

  family                   = "${local.name}-migrations"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.migrations_task.arn
  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([{
    name        = "catalog-migrations"
    image       = "${aws_ecr_repository.service["migrations"].repository_url}:${var.migrations_image_tag}"
    essential   = true
    environment = [for key, value in merge(local.common_service_environment, { REGISTRY_DATABASE_USER = var.database_master_username }) : { name = key, value = value }]
    logConfiguration = {
      logDriver = "awslogs"
      options   = { awslogs-group = aws_cloudwatch_log_group.service["migrations"].name, awslogs-region = var.aws_region, awslogs-stream-prefix = "migrations" }
    }
    linuxParameters        = { initProcessEnabled = true }
    readonlyRootFilesystem = true
    stopTimeout            = 120
  }])

  tags = local.common_tags
}

resource "aws_ecs_service" "ui" {
  count = var.deploy_application_services ? 1 : 0

  name                               = "${local.name}-ui"
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.ui[0].arn
  desired_count                      = var.ui_desired_count
  launch_type                        = "FARGATE"
  platform_version                   = var.fargate_platform_version
  deployment_minimum_healthy_percent = var.ecs_deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.ecs_deployment_maximum_percent
  health_check_grace_period_seconds  = 60
  enable_execute_command             = var.enable_execute_command
  propagate_tags                     = "SERVICE"
  wait_for_steady_state              = true

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = values(aws_subnet.application)[*].id
    security_groups  = [aws_security_group.ui.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.ui.arn
    container_name   = "registry-web"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.https]
  tags       = local.common_tags
}

resource "aws_ecs_service" "api" {
  count = var.deploy_application_services ? 1 : 0

  name                               = "${local.name}-api"
  cluster                            = aws_ecs_cluster.this.id
  task_definition                    = aws_ecs_task_definition.api[0].arn
  desired_count                      = var.api_desired_count
  launch_type                        = "FARGATE"
  platform_version                   = var.fargate_platform_version
  deployment_minimum_healthy_percent = var.ecs_deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.ecs_deployment_maximum_percent
  health_check_grace_period_seconds  = 120
  enable_execute_command             = var.enable_execute_command
  propagate_tags                     = "SERVICE"
  wait_for_steady_state              = true

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = values(aws_subnet.application)[*].id
    security_groups  = [aws_security_group.services.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "catalog-api"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener_rule.api, aws_db_proxy_target.this]
  tags       = local.common_tags
}
