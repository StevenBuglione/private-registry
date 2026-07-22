resource "aws_cloudwatch_log_group" "opensearch_application" {
  name              = "/aws/opensearch/${local.name}/application"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "opensearch_slow_search" {
  name              = "/aws/opensearch/${local.name}/slow-search"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn
  tags              = local.common_tags
}

data "aws_iam_policy_document" "opensearch_logs" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["es.amazonaws.com"]
    }
    actions = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [
      "${aws_cloudwatch_log_group.opensearch_application.arn}:*",
      "${aws_cloudwatch_log_group.opensearch_slow_search.arn}:*"
    ]
  }
}

resource "aws_cloudwatch_log_resource_policy" "opensearch" {
  policy_name     = "${local.name}-opensearch-logs"
  policy_document = data.aws_iam_policy_document.opensearch_logs.json
}

data "aws_iam_policy_document" "opensearch_access" {
  statement {
    effect = "Allow"
    principals {
      type = "AWS"
      identifiers = [
        aws_iam_role.api_task.arn,
        aws_iam_role.indexer_task.arn,
        aws_iam_role.reconciler_task.arn,
        aws_iam_role.opensearch_admin.arn
      ]
    }
    actions   = ["es:ESHttp*"]
    resources = ["arn:${data.aws_partition.current.partition}:es:${var.aws_region}:${data.aws_caller_identity.current.account_id}:domain/${local.name}/*"]
  }
}

resource "aws_opensearch_domain" "this" {
  domain_name    = local.name
  engine_version = var.opensearch_engine_version

  cluster_config {
    instance_type                 = var.opensearch_data_instance_type
    instance_count                = var.opensearch_data_instance_count
    dedicated_master_enabled      = true
    dedicated_master_type         = var.opensearch_master_instance_type
    dedicated_master_count        = 3
    zone_awareness_enabled        = true
    multi_az_with_standby_enabled = true
    zone_awareness_config {
      availability_zone_count = var.opensearch_zone_awareness_count
    }
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.opensearch_ebs_volume_size
    throughput  = 250
    iops        = 3000
  }

  vpc_options {
    subnet_ids         = values(aws_subnet.data)[*].id
    security_group_ids = [aws_security_group.opensearch.id]
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }

  encrypt_at_rest {
    enabled    = true
    kms_key_id = aws_kms_key.data.arn
  }

  node_to_node_encryption { enabled = true }

  advanced_security_options {
    enabled                        = true
    internal_user_database_enabled = false
    anonymous_auth_enabled         = false
    master_user_options { master_user_arn = aws_iam_role.opensearch_admin.arn }
  }

  auto_tune_options { desired_state = "ENABLED" }

  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch_application.arn
    log_type                 = "ES_APPLICATION_LOGS"
    enabled                  = true
  }
  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch_slow_search.arn
    log_type                 = "SEARCH_SLOW_LOGS"
    enabled                  = true
  }

  access_policies = data.aws_iam_policy_document.opensearch_access.json
  tags            = local.common_tags

  depends_on = [aws_cloudwatch_log_resource_policy.opensearch]

  lifecycle {
    precondition {
      condition     = var.opensearch_data_instance_count % 3 == 0 && var.opensearch_zone_awareness_count == 3
      error_message = "Multi-AZ with Standby requires three Availability Zones and a data-node count divisible by three."
    }
  }
}
