resource "aws_cloudwatch_log_group" "vpc_flow_logs" {
  name              = "/aws/vpc/${local.name}/flow-logs"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn
  tags              = local.common_tags
}

resource "aws_sns_topic" "alarms" {
  name              = "${local.name}-alarms"
  kms_master_key_id = aws_kms_key.queue.arn
  tags              = local.common_tags
}



data "aws_iam_policy_document" "alarm_topic" {
  statement {
    sid       = "OwnerAdministration"
    effect    = "Allow"
    actions   = ["sns:*"]
    resources = [aws_sns_topic.alarms.arn]
    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }

  statement {
    sid       = "CloudWatchAlarmPublishing"
    effect    = "Allow"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alarms.arn]
    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "alarms" {
  arn    = aws_sns_topic.alarms.arn
  policy = data.aws_iam_policy_document.alarm_topic.json
}

resource "aws_sns_topic_subscription" "email" {
  count = var.alarm_notification_email == null ? 0 : 1

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_notification_email
}

locals {
  alarm_actions = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "dlq_messages" {
  alarm_name          = "${local.name}-dlq-messages"
  alarm_description   = "Any message in the ingestion dead-letter queue requires investigation."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  dimensions          = { QueueName = aws_sqs_queue.dlq.name }
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
  treat_missing_data  = "notBreaching"
  tags                = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "oldest_message" {
  alarm_name          = "${local.name}-oldest-message"
  alarm_description   = "Catalog ingestion is not keeping up with release events."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ApproximateAgeOfOldestMessage"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 300
  dimensions          = { QueueName = aws_sqs_queue.ingestion.name }
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
  treat_missing_data  = "notBreaching"
  tags                = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${local.name}-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_ELB_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  dimensions          = { LoadBalancer = aws_lb.this.arn_suffix }
  alarm_actions       = local.alarm_actions
  treat_missing_data  = "notBreaching"
  tags                = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "api_target_5xx" {
  alarm_name          = "${local.name}-api-target-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  dimensions = {
    LoadBalancer = aws_lb.this.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }
  alarm_actions      = local.alarm_actions
  treat_missing_data = "notBreaching"
  tags               = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "aurora_cpu" {
  alarm_name          = "${local.name}-aurora-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  dimensions          = { DBClusterIdentifier = local.selected_cluster_id }
  alarm_actions       = local.alarm_actions
  treat_missing_data  = "missing"
  tags                = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "opensearch_red" {
  alarm_name          = "${local.name}-opensearch-red"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ClusterStatus.red"
  namespace           = "AWS/ES"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  dimensions = {
    ClientId   = data.aws_caller_identity.current.account_id
    DomainName = aws_opensearch_domain.this.domain_name
  }
  alarm_actions      = local.alarm_actions
  treat_missing_data = "breaching"
  tags               = local.common_tags
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = local.name
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6,
        properties = {
          title = "ALB requests and errors", region = var.aws_region, view = "timeSeries", stacked = false,
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", aws_lb.this.arn_suffix],
            [".", "HTTPCode_ELB_5XX_Count", ".", "."],
            [".", "TargetResponseTime", ".", ".", { stat = "p95" }]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6,
        properties = {
          title = "Ingestion queue", region = var.aws_region, view = "timeSeries",
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.ingestion.name],
            [".", "ApproximateAgeOfOldestMessage", ".", "."],
            [".", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.dlq.name]
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6,
        properties = {
          title = "ECS utilization", region = var.aws_region, view = "timeSeries",
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", var.deploy_application_services ? aws_ecs_service.api[0].name : "not-deployed"],
            [".", "MemoryUtilization", ".", ".", ".", "."]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 6, width = 12, height = 6,
        properties = {
          title = "Aurora and OpenSearch", region = var.aws_region, view = "timeSeries",
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBClusterIdentifier", local.selected_cluster_id],
            ["AWS/ES", "ClusterStatus.red", "ClientId", data.aws_caller_identity.current.account_id, "DomainName", aws_opensearch_domain.this.domain_name],
            [".", "JVMMemoryPressure", ".", ".", ".", "."]
          ]
        }
      }
    ]
  })
}
