resource "aws_sqs_queue" "dlq" {
  name                      = "${local.name}-ingestion-dlq"
  message_retention_seconds = var.dlq_retention_seconds
  kms_master_key_id         = aws_kms_key.queue.arn
  tags                      = local.common_tags
}

resource "aws_sqs_queue" "ingestion" {
  name                       = "${local.name}-ingestion"
  visibility_timeout_seconds = var.ingestion_visibility_timeout_seconds
  message_retention_seconds  = var.ingestion_retention_seconds
  receive_wait_time_seconds  = 20
  kms_master_key_id          = aws_kms_key.queue.arn
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = var.ingestion_max_receive_count
  })
  tags = local.common_tags
}

resource "aws_cloudwatch_event_bus" "catalog" {
  name = local.name
  tags = local.common_tags
}

data "aws_iam_policy_document" "event_bus" {
  statement {
    sid       = "AllowAccountEvents"
    effect    = "Allow"
    actions   = ["events:PutEvents"]
    resources = [aws_cloudwatch_event_bus.catalog.arn]
    principals {
      type        = "AWS"
      identifiers = concat(["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"], var.event_publisher_principal_arns)
    }
  }
}

resource "aws_cloudwatch_event_bus_policy" "catalog" {
  event_bus_name = aws_cloudwatch_event_bus.catalog.name
  policy         = data.aws_iam_policy_document.event_bus.json
}

resource "aws_cloudwatch_event_rule" "package_lifecycle" {
  name           = "${local.name}-package-lifecycle"
  event_bus_name = aws_cloudwatch_event_bus.catalog.name
  event_pattern = jsonencode({
    source      = ["private-registry.release"]
    detail-type = ["PackagePromoted", "PackageDeprecated", "PackageRevoked"]
  })
  tags = local.common_tags
}

resource "aws_cloudwatch_event_target" "ingestion" {
  rule           = aws_cloudwatch_event_rule.package_lifecycle.name
  event_bus_name = aws_cloudwatch_event_bus.catalog.name
  target_id      = "catalog-ingestion-queue"
  arn            = aws_sqs_queue.ingestion.arn
  dead_letter_config { arn = aws_sqs_queue.dlq.arn }
}

data "aws_iam_policy_document" "ingestion_queue" {
  statement {
    sid       = "AllowEventBridge"
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.ingestion.arn]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.package_lifecycle.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "ingestion" {
  queue_url = aws_sqs_queue.ingestion.id
  policy    = data.aws_iam_policy_document.ingestion_queue.json
}

resource "aws_cloudwatch_event_archive" "catalog" {
  name             = "${local.name}-events"
  event_source_arn = aws_cloudwatch_event_bus.catalog.arn
  retention_days   = 90
  event_pattern = jsonencode({
    source = ["private-registry.release"]
  })
}

data "aws_iam_policy_document" "dlq" {
  statement {
    sid       = "AllowEventBridgeDeadLetters"
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq.arn]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.package_lifecycle.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "dlq" {
  queue_url = aws_sqs_queue.dlq.id
  policy    = data.aws_iam_policy_document.dlq.json
}
