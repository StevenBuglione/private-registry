resource "aws_db_subnet_group" "this" {
  name       = local.name
  subnet_ids = values(aws_subnet.data)[*].id
  tags       = merge(local.common_tags, { Name = local.name })
}

resource "aws_rds_cluster_parameter_group" "this" {
  name_prefix = "${local.name}-cluster-"
  family      = "aurora-postgresql16"
  description = "${local.name} Aurora PostgreSQL cluster parameters"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "log_min_duration_statement"
    value        = "1000"
    apply_method = "immediate"
  }

  tags = local.common_tags
  lifecycle { create_before_destroy = true }
}

resource "aws_db_parameter_group" "this" {
  name_prefix = "${local.name}-instance-"
  family      = "aurora-postgresql16"
  description = "${local.name} Aurora PostgreSQL instance parameters"
  tags        = local.common_tags
  lifecycle { create_before_destroy = true }
}

resource "aws_rds_global_cluster" "this" {
  count = var.enable_aurora_global_database && !var.is_dr_region ? 1 : 0

  global_cluster_identifier = coalesce(var.aurora_global_cluster_identifier, "${local.name}-global")
  engine                    = "aurora-postgresql"
  engine_version            = var.aurora_engine_version
  database_name             = var.database_name
  storage_encrypted         = true
  deletion_protection       = var.database_deletion_protection
}

resource "aws_rds_cluster" "primary" {
  count = var.is_dr_region ? 0 : 1

  cluster_identifier                  = local.name
  engine                              = "aurora-postgresql"
  engine_version                      = var.aurora_engine_version
  engine_mode                         = "provisioned"
  database_name                       = var.database_name
  master_username                     = var.database_master_username
  manage_master_user_password         = true
  master_user_secret_kms_key_id       = aws_kms_key.data.arn
  global_cluster_identifier           = var.enable_aurora_global_database ? aws_rds_global_cluster.this[0].id : null
  db_subnet_group_name                = aws_db_subnet_group.this.name
  db_cluster_parameter_group_name     = aws_rds_cluster_parameter_group.this.name
  vpc_security_group_ids              = [aws_security_group.database.id]
  storage_encrypted                   = true
  kms_key_id                          = aws_kms_key.data.arn
  backup_retention_period             = var.database_backup_retention_days
  preferred_backup_window             = "03:00-04:00"
  preferred_maintenance_window        = "sun:05:00-sun:06:00"
  copy_tags_to_snapshot               = true
  deletion_protection                 = var.database_deletion_protection
  skip_final_snapshot                 = var.database_skip_final_snapshot
  final_snapshot_identifier           = var.database_skip_final_snapshot ? null : "${local.name}-final"
  enabled_cloudwatch_logs_exports     = ["postgresql"]
  iam_database_authentication_enabled = true
  tags                                = local.common_tags

  lifecycle {
    ignore_changes = [final_snapshot_identifier]
  }
}

resource "aws_rds_cluster" "secondary" {
  count = var.is_dr_region ? 1 : 0

  cluster_identifier                  = local.name
  engine                              = "aurora-postgresql"
  engine_version                      = var.aurora_engine_version
  engine_mode                         = "provisioned"
  global_cluster_identifier           = var.aurora_global_cluster_identifier
  source_region                       = var.primary_region
  db_subnet_group_name                = aws_db_subnet_group.this.name
  db_cluster_parameter_group_name     = aws_rds_cluster_parameter_group.this.name
  vpc_security_group_ids              = [aws_security_group.database.id]
  storage_encrypted                   = true
  kms_key_id                          = aws_kms_key.data.arn
  backup_retention_period             = var.database_backup_retention_days
  preferred_backup_window             = "03:00-04:00"
  preferred_maintenance_window        = "sun:05:00-sun:06:00"
  copy_tags_to_snapshot               = true
  deletion_protection                 = var.database_deletion_protection
  skip_final_snapshot                 = var.database_skip_final_snapshot
  final_snapshot_identifier           = var.database_skip_final_snapshot ? null : "${local.name}-final"
  enabled_cloudwatch_logs_exports     = ["postgresql"]
  iam_database_authentication_enabled = true
  tags                                = local.common_tags

  lifecycle {
    ignore_changes = [final_snapshot_identifier]
    precondition {
      condition     = var.enable_aurora_global_database && var.aurora_global_cluster_identifier != null && var.primary_region != null
      error_message = "A DR Region requires enable_aurora_global_database, aurora_global_cluster_identifier, and primary_region."
    }
  }
}

resource "aws_rds_cluster_instance" "this" {
  count = var.aurora_instance_count

  identifier                            = "${local.name}-${count.index + 1}"
  cluster_identifier                    = local.selected_cluster_id
  instance_class                        = var.aurora_instance_class
  engine                                = "aurora-postgresql"
  engine_version                        = var.aurora_engine_version
  db_parameter_group_name               = aws_db_parameter_group.this.name
  publicly_accessible                   = false
  auto_minor_version_upgrade            = true
  performance_insights_enabled          = true
  performance_insights_kms_key_id       = aws_kms_key.data.arn
  performance_insights_retention_period = var.database_performance_insights_retention_days
  monitoring_interval                   = var.database_monitoring_interval
  monitoring_role_arn                   = var.database_monitoring_interval > 0 ? aws_iam_role.rds_monitoring.arn : null
  tags                                  = merge(local.common_tags, { Role = count.index == 0 ? "writer" : "reader" })
}

resource "aws_iam_role" "rds_proxy" {
  name = "${local.name}-rds-proxy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
  tags = local.common_tags
}

data "aws_iam_policy_document" "rds_proxy" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = local.database_proxy_secret_arn == null ? ["arn:${data.aws_partition.current.partition}:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${local.name}-placeholder"] : [local.database_proxy_secret_arn]
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = distinct(compact([aws_kms_key.data.arn, var.database_secret_kms_key_arn]))
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${var.aws_region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "rds_proxy" {
  name   = "read-database-secret"
  role   = aws_iam_role.rds_proxy.id
  policy = data.aws_iam_policy_document.rds_proxy.json
}

resource "aws_db_proxy" "this" {
  name                   = local.name
  debug_logging          = false
  engine_family          = "POSTGRESQL"
  idle_client_timeout    = 1800
  require_tls            = true
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_security_group_ids = [aws_security_group.database_proxy.id]
  vpc_subnet_ids         = values(aws_subnet.data)[*].id

  auth {
    auth_scheme = "SECRETS"
    description = "Application database credentials"
    iam_auth    = "REQUIRED"
    secret_arn  = local.database_proxy_secret_arn
  }

  tags = local.common_tags

  lifecycle {
    precondition {
      condition     = local.database_proxy_secret_arn != null
      error_message = "database_proxy_secret_arn or an available primary master secret is required. Supply a replicated application secret in the DR Region."
    }
  }
}

resource "aws_db_proxy_default_target_group" "this" {
  db_proxy_name = aws_db_proxy.this.name
  connection_pool_config {
    connection_borrow_timeout    = 120
    max_connections_percent      = 80
    max_idle_connections_percent = 50
  }
}

resource "aws_db_proxy_target" "this" {
  db_cluster_identifier = local.selected_cluster_id
  db_proxy_name         = aws_db_proxy.this.name
  target_group_name     = aws_db_proxy_default_target_group.this.name
  depends_on            = [aws_rds_cluster_instance.this]
}
