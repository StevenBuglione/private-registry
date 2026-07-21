data "aws_iam_policy_document" "kms_base" {
  statement {
    sid       = "EnableAccountPermissions"
    effect    = "Allow"
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }
}

data "aws_iam_policy_document" "kms_logs" {
  source_policy_documents = [data.aws_iam_policy_document.kms_base.json]

  statement {
    sid    = "AllowCloudWatchLogs"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey*",
      "kms:ReEncrypt*"
    ]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["logs.${var.aws_region}.amazonaws.com"]
    }
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:${data.aws_partition.current.partition}:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:*"]
    }
  }
}

data "aws_iam_policy_document" "kms_queue" {
  source_policy_documents = [data.aws_iam_policy_document.kms_base.json]

  statement {
    sid    = "AllowEventDeliveryServices"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:GenerateDataKey*"
    ]
    resources = ["*"]
    principals {
      type = "Service"
      identifiers = [
        "cloudwatch.amazonaws.com",
        "events.amazonaws.com",
        "scheduler.amazonaws.com",
        "sns.amazonaws.com"
      ]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_kms_key" "data" {
  description             = "${local.name} data encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_base.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "data" {
  name          = "alias/${local.name}-data"
  target_key_id = aws_kms_key.data.key_id
}

resource "aws_kms_key" "logs" {
  description             = "${local.name} log encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_logs.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "logs" {
  name          = "alias/${local.name}-logs"
  target_key_id = aws_kms_key.logs.key_id
}

resource "aws_kms_key" "queue" {
  description             = "${local.name} event, queue, and alarm encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_queue.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "queue" {
  name          = "alias/${local.name}-queue"
  target_key_id = aws_kms_key.queue.key_id
}

resource "aws_kms_key" "backup" {
  description             = "${local.name} backup encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.kms_base.json
  tags                    = local.common_tags
}

resource "aws_kms_alias" "backup" {
  name          = "alias/${local.name}-backup"
  target_key_id = aws_kms_key.backup.key_id
}
