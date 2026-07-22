locals {
  scalable_services = var.deploy_application_services ? {
    ui      = { resource_id = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.ui[0].name}", min = var.ui_min_count, max = var.ui_max_count }
    api     = { resource_id = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.api[0].name}", min = var.api_min_count, max = var.api_max_count }
    indexer = { resource_id = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.indexer[0].name}", min = var.indexer_min_count, max = var.indexer_max_count }
  } : {}
}

resource "aws_appautoscaling_target" "service" {
  for_each = local.scalable_services

  max_capacity       = each.value.max
  min_capacity       = each.value.min
  resource_id        = each.value.resource_id
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each = aws_appautoscaling_target.service

  name               = "${local.name}-${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = each.value.resource_id
  scalable_dimension = each.value.scalable_dimension
  service_namespace  = each.value.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageCPUUtilization" }
  }
}

resource "aws_appautoscaling_policy" "indexer_queue_scale_out" {
  count = var.deploy_application_services ? 1 : 0

  name               = "${local.name}-indexer-queue-out"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.service["indexer"].resource_id
  scalable_dimension = aws_appautoscaling_target.service["indexer"].scalable_dimension
  service_namespace  = aws_appautoscaling_target.service["indexer"].service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Maximum"
    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 2
    }
    step_adjustment {
      metric_interval_lower_bound = 100
      scaling_adjustment          = 5
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "indexer_queue_scale_out" {
  count = var.deploy_application_services ? 1 : 0

  alarm_name          = "${local.name}-indexer-queue-scale-out"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 20
  dimensions          = { QueueName = aws_sqs_queue.ingestion.name }
  alarm_actions       = [aws_appautoscaling_policy.indexer_queue_scale_out[0].arn]
  tags                = local.common_tags
}
