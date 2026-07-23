locals {
  scalable_services = var.deploy_application_services ? {
    ui  = { resource_id = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.ui[0].name}", min = var.ui_min_count, max = var.ui_max_count }
    api = { resource_id = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.api[0].name}", min = var.api_min_count, max = var.api_max_count }
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
