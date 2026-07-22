provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge({
      ManagedBy = "Terraform"
      Component = "private-registry-state"
    }, var.tags)
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

data "aws_iam_policy_document" "state_key" {
  statement {
    sid    = "RootAdministration"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = length(var.state_key_administrator_arns) == 0 ? [] : [1]
    content {
      sid    = "DelegatedAdministration"
      effect = "Allow"
      principals {
        type        = "AWS"
        identifiers = var.state_key_administrator_arns
      }
      actions   = ["kms:Create*", "kms:Describe*", "kms:Enable*", "kms:List*", "kms:Put*", "kms:Update*", "kms:Revoke*", "kms:Disable*", "kms:Get*", "kms:Delete*", "kms:TagResource", "kms:UntagResource", "kms:ScheduleKeyDeletion", "kms:CancelKeyDeletion"]
      resources = ["*"]
    }
  }

  dynamic "statement" {
    for_each = length(var.state_reader_writer_arns) == 0 ? [] : [1]
    content {
      sid    = "StateUse"
      effect = "Allow"
      principals {
        type        = "AWS"
        identifiers = var.state_reader_writer_arns
      }
      actions   = ["kms:Decrypt", "kms:DescribeKey", "kms:Encrypt", "kms:GenerateDataKey", "kms:ReEncrypt*"]
      resources = ["*"]
    }
  }
}

resource "aws_kms_key" "state" {
  description             = "Terraform state encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.state_key.json
}

resource "aws_kms_alias" "state" {
  name          = "alias/private-registry-terraform-state"
  target_key_id = aws_kms_key.state.key_id
}

resource "aws_s3_bucket" "logs" {
  bucket        = var.log_bucket_name
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_public_access_block" "logs" {
  bucket                  = aws_s3_bucket.logs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule { object_ownership = "BucketOwnerPreferred" }
}

resource "aws_s3_bucket_versioning" "logs" {
  bucket = aws_s3_bucket.logs.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule {
    id     = "expire-old-access-logs"
    status = "Enabled"
    filter {}
    expiration { days = 365 }
    noncurrent_version_expiration { noncurrent_days = 30 }
  }
}

resource "aws_s3_bucket" "state" {
  bucket        = var.state_bucket_name
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "state" {
  bucket = aws_s3_bucket.state.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.state.arn
      sse_algorithm     = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_logging" "state" {
  bucket        = aws_s3_bucket.state.id
  target_bucket = aws_s3_bucket.logs.id
  target_prefix = "state-access/"
  depends_on    = [aws_s3_bucket_ownership_controls.logs]
}

resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    id     = "retain-state-history"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration { noncurrent_days = 365 }
  }
}

data "aws_iam_policy_document" "state_bucket" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid       = "DenyUnencryptedObjectWrites"
    effect    = "Deny"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.state.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "StringNotEquals"
      variable = "s3:x-amz-server-side-encryption"
      values   = ["aws:kms"]
    }
  }

  dynamic "statement" {
    for_each = length(var.state_reader_writer_arns) == 0 ? [] : [1]
    content {
      sid    = "StateAccess"
      effect = "Allow"
      principals {
        type        = "AWS"
        identifiers = var.state_reader_writer_arns
      }
      actions   = ["s3:ListBucket", "s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
      resources = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
    }
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = data.aws_iam_policy_document.state_bucket.json
}
