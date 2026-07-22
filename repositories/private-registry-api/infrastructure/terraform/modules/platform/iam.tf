data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ui_task" {
  name               = "${local.name}-ui-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role" "api_task" {
  name               = "${local.name}-api-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role" "indexer_task" {
  name               = "${local.name}-indexer-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role" "reconciler_task" {
  name               = "${local.name}-reconciler-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = local.common_tags
}

resource "aws_iam_role" "migrations_task" {
  name               = "${local.name}-migrations-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = local.common_tags
}

data "aws_iam_policy_document" "api_task" {
  statement {
    sid     = "ReadCatalogDocuments"
    actions = ["s3:GetObject", "s3:GetObjectVersion", "s3:ListBucket"]
    resources = [
      aws_s3_bucket.this["documentation"].arn,
      "${aws_s3_bucket.this["documentation"].arn}/*"
    ]
  }
  statement {
    sid       = "QueryOpenSearch"
    actions   = ["es:ESHttpGet", "es:ESHttpHead", "es:ESHttpPost"]
    resources = ["${aws_opensearch_domain.this.arn}/*"]
  }
  statement {
    sid       = "DatabaseIAMConnect"
    actions   = ["rds-db:connect"]
    resources = ["arn:${data.aws_partition.current.partition}:rds-db:${var.aws_region}:${data.aws_caller_identity.current.account_id}:dbuser:*/registry_app"]
  }
  dynamic "statement" {
    for_each = var.authorization_config_secret_arn == null ? [] : [1]
    content {
      sid       = "AuthorizationConfiguration"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = [var.authorization_config_secret_arn]
    }
  }
  statement {
    sid       = "DecryptData"
    actions   = ["kms:Decrypt", "kms:DescribeKey"]
    resources = distinct(compact([aws_kms_key.data.arn, var.authorization_secret_kms_key_arn]))
  }
}

resource "aws_iam_role_policy" "api_task" {
  name   = "catalog-api"
  role   = aws_iam_role.api_task.id
  policy = data.aws_iam_policy_document.api_task.json
}

data "aws_iam_policy_document" "indexer_task" {
  statement {
    sid       = "ConsumeIngestionQueue"
    actions   = ["sqs:ChangeMessageVisibility", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:GetQueueUrl", "sqs:ReceiveMessage"]
    resources = [aws_sqs_queue.ingestion.arn]
  }
  statement {
    sid     = "WriteDocumentsAndEvidence"
    actions = ["s3:AbortMultipartUpload", "s3:GetObject", "s3:ListBucket", "s3:PutObject"]
    resources = [
      aws_s3_bucket.this["documentation"].arn,
      "${aws_s3_bucket.this["documentation"].arn}/*",
      aws_s3_bucket.this["quarantine"].arn,
      "${aws_s3_bucket.this["quarantine"].arn}/*",
      aws_s3_bucket.this["audit"].arn,
      "${aws_s3_bucket.this["audit"].arn}/*"
    ]
  }
  statement {
    sid       = "ManageOpenSearchDocuments"
    actions   = ["es:ESHttpDelete", "es:ESHttpGet", "es:ESHttpHead", "es:ESHttpPost", "es:ESHttpPut"]
    resources = ["${aws_opensearch_domain.this.arn}/*"]
  }
  statement {
    sid       = "DatabaseIAMConnect"
    actions   = ["rds-db:connect"]
    resources = ["arn:${data.aws_partition.current.partition}:rds-db:${var.aws_region}:${data.aws_caller_identity.current.account_id}:dbuser:*/registry_indexer"]
  }
  dynamic "statement" {
    for_each = var.jfrog_token_secret_arn == null ? [] : [1]
    content {
      sid       = "ReadJFrogCredential"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = [var.jfrog_token_secret_arn]
    }
  }
  statement {
    sid       = "UseEncryptionKeys"
    actions   = ["kms:Decrypt", "kms:DescribeKey", "kms:Encrypt", "kms:GenerateDataKey"]
    resources = distinct(compact([aws_kms_key.data.arn, aws_kms_key.queue.arn, var.jfrog_secret_kms_key_arn]))
  }
}

resource "aws_iam_role_policy" "indexer_task" {
  name   = "catalog-indexer"
  role   = aws_iam_role.indexer_task.id
  policy = data.aws_iam_policy_document.indexer_task.json
}

data "aws_iam_policy_document" "reconciler_task" {
  source_policy_documents = [data.aws_iam_policy_document.indexer_task.json]
  statement {
    sid     = "WriteReconciliationReports"
    actions = ["s3:GetObject", "s3:ListBucket", "s3:PutObject"]
    resources = [
      aws_s3_bucket.this["reconciliation"].arn,
      "${aws_s3_bucket.this["reconciliation"].arn}/*"
    ]
  }
}

resource "aws_iam_role_policy" "reconciler_task" {
  name   = "catalog-reconciler"
  role   = aws_iam_role.reconciler_task.id
  policy = data.aws_iam_policy_document.reconciler_task.json
}

data "aws_iam_policy_document" "migrations_task" {
  statement {
    sid       = "DatabaseIAMConnect"
    actions   = ["rds-db:connect"]
    resources = ["arn:${data.aws_partition.current.partition}:rds-db:${var.aws_region}:${data.aws_caller_identity.current.account_id}:dbuser:*/${var.database_master_username}"]
  }
  statement {
    sid       = "ReadMasterSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = local.database_proxy_secret_arn == null ? ["arn:${data.aws_partition.current.partition}:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${local.name}-placeholder"] : [local.database_proxy_secret_arn]
  }
  statement {
    sid       = "DecryptDatabaseSecret"
    actions   = ["kms:Decrypt"]
    resources = distinct(compact([aws_kms_key.data.arn, var.database_secret_kms_key_arn]))
  }
}

resource "aws_iam_role_policy" "migrations_task" {
  name   = "catalog-migrations"
  role   = aws_iam_role.migrations_task.id
  policy = data.aws_iam_policy_document.migrations_task.json
}

resource "aws_iam_role" "scheduler" {
  name = "${local.name}-scheduler"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "scheduler.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
  tags = local.common_tags
}

data "aws_iam_policy_document" "scheduler" {
  statement {
    actions   = ["ecs:RunTask"]
    resources = var.deploy_application_services ? [aws_ecs_task_definition.reconciler[0].arn] : ["arn:${data.aws_partition.current.partition}:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${local.name}-reconciler:*"]
    condition {
      test     = "ArnLike"
      variable = "ecs:cluster"
      values   = [aws_ecs_cluster.this.arn]
    }
  }
  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn, aws_iam_role.reconciler_task.arn]
  }
  statement {
    sid       = "WriteSchedulerDeadLetters"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq.arn]
  }
  statement {
    sid       = "UseSchedulerDeadLetterKey"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.queue.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "run-reconciliation-task"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler.json
}

resource "aws_iam_role" "vpc_flow_logs" {
  name = "${local.name}-vpc-flow-logs"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "vpc-flow-logs.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy" "vpc_flow_logs" {
  name = "write-cloudwatch-logs"
  role = aws_iam_role.vpc_flow_logs.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:DescribeLogGroups", "logs:DescribeLogStreams", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.vpc_flow_logs.arn}:*"
    }]
  })
}

resource "aws_iam_role" "rds_monitoring" {
  name = "${local.name}-rds-monitoring"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "monitoring.rds.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_iam_role" "opensearch_admin" {
  name = "${local.name}-opensearch-admin"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { AWS = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root" }, Action = "sts:AssumeRole" }]
  })
  tags = local.common_tags
}
