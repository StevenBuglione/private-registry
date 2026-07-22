resource "aws_backup_vault" "this" {
  name        = local.name
  kms_key_arn = aws_kms_key.backup.arn
  tags        = local.common_tags
}

resource "aws_iam_role" "backup" {
  name = "${local.name}-backup"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "backup.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "backup" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup"
}

resource "aws_iam_role_policy_attachment" "backup_restore" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForRestores"
}

resource "aws_backup_plan" "this" {
  name = local.name

  rule {
    rule_name         = "daily"
    target_vault_name = aws_backup_vault.this.name
    schedule          = "cron(0 5 ? * * *)"
    start_window      = 60
    completion_window = 360
    lifecycle {
      cold_storage_after = var.backup_cold_storage_after_days
      delete_after       = var.backup_delete_after_days
    }

    dynamic "copy_action" {
      for_each = var.backup_copy_destination_vault_arn == null ? [] : [var.backup_copy_destination_vault_arn]
      content {
        destination_vault_arn = copy_action.value
        lifecycle {
          cold_storage_after = var.backup_cold_storage_after_days
          delete_after       = var.backup_delete_after_days
        }
      }
    }

    recovery_point_tags = local.common_tags
  }

  tags = local.common_tags
}

resource "aws_backup_selection" "aurora" {
  iam_role_arn = aws_iam_role.backup.arn
  name         = "${local.name}-aurora"
  plan_id      = aws_backup_plan.this.id
  resources    = compact([local.selected_cluster_arn])
}
