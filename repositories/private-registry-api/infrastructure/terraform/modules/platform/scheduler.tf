resource "aws_scheduler_schedule_group" "reconciliation" {
  name = "${local.name}-reconciliation"
  tags = local.common_tags
}

resource "aws_scheduler_schedule" "incremental_reconciliation" {
  count = var.deploy_application_services ? 1 : 0

  name                         = "${local.name}-incremental-reconciliation"
  group_name                   = aws_scheduler_schedule_group.reconciliation.name
  schedule_expression          = var.reconciliation_schedule_expression
  schedule_expression_timezone = "UTC"
  state                        = "ENABLED"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn
    input = jsonencode({
      containerOverrides = [{
        name = "catalog-reconciler"
        environment = [
          { name = "REGISTRY_RECONCILIATION_SCOPE", value = "incremental" },
          { name = "REGISTRY_RECONCILIATION_MODE", value = "dry-run" }
        ]
      }]
    })

    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 2
    }

    dead_letter_config { arn = aws_sqs_queue.dlq.arn }

    ecs_parameters {
      task_definition_arn     = aws_ecs_task_definition.reconciler[0].arn
      task_count              = 1
      launch_type             = "FARGATE"
      platform_version        = var.fargate_platform_version
      enable_ecs_managed_tags = true
      propagate_tags          = "TASK_DEFINITION"
      network_configuration {
        assign_public_ip = false
        security_groups  = [aws_security_group.services.id]
        subnets          = values(aws_subnet.application)[*].id
      }
    }
  }
}

resource "aws_scheduler_schedule" "full_reconciliation" {
  count = var.deploy_application_services ? 1 : 0

  name                         = "${local.name}-full-reconciliation"
  group_name                   = aws_scheduler_schedule_group.reconciliation.name
  schedule_expression          = var.full_reconciliation_schedule_expression
  schedule_expression_timezone = "UTC"
  state                        = "ENABLED"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn
    input = jsonencode({
      containerOverrides = [{
        name = "catalog-reconciler"
        environment = [
          { name = "REGISTRY_RECONCILIATION_SCOPE", value = "full" },
          { name = "REGISTRY_RECONCILIATION_MODE", value = "dry-run" }
        ]
      }]
    })

    retry_policy {
      maximum_event_age_in_seconds = 7200
      maximum_retry_attempts       = 1
    }

    dead_letter_config { arn = aws_sqs_queue.dlq.arn }

    ecs_parameters {
      task_definition_arn     = aws_ecs_task_definition.reconciler[0].arn
      task_count              = 1
      launch_type             = "FARGATE"
      platform_version        = var.fargate_platform_version
      enable_ecs_managed_tags = true
      propagate_tags          = "TASK_DEFINITION"
      network_configuration {
        assign_public_ip = false
        security_groups  = [aws_security_group.services.id]
        subnets          = values(aws_subnet.application)[*].id
      }
    }
  }
}
