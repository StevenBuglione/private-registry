locals {
  buckets = {
    documentation  = { prefix = "${local.name}-docs-", retention = var.documentation_retention_days }
    quarantine     = { prefix = "${local.name}-quarantine-", retention = var.quarantine_retention_days }
    audit          = { prefix = "${local.name}-audit-", retention = var.audit_object_lock_retention_days }
    reconciliation = { prefix = "${local.name}-reconciliation-", retention = var.documentation_retention_days }
    alb_logs       = { prefix = "${local.name}-alb-logs-", retention = 365 }
  }
}

resource "aws_s3_bucket" "this" {
  for_each = local.buckets

  bucket_prefix       = each.value.prefix
  force_destroy       = var.force_destroy_buckets
  object_lock_enabled = each.key == "audit" ? var.audit_object_lock_enabled : false
  tags                = merge(local.common_tags, { Purpose = each.key })
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each = aws_s3_bucket.this

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule { object_ownership = each.key == "alb_logs" ? "BucketOwnerPreferred" : "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_versioning" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = each.key == "alb_logs" ? null : aws_kms_key.data.arn
      sse_algorithm     = each.key == "alb_logs" ? "AES256" : "aws:kms"
    }
    bucket_key_enabled = each.key != "alb_logs"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  for_each   = aws_s3_bucket.this
  bucket     = each.value.id
  depends_on = [aws_s3_bucket_versioning.this]

  rule {
    id     = "retention"
    status = "Enabled"
    filter {}
    expiration { days = local.buckets[each.key].retention }
    noncurrent_version_expiration { noncurrent_days = min(local.buckets[each.key].retention, 365) }
    abort_incomplete_multipart_upload { days_after_initiation = 7 }
  }
}

resource "aws_s3_bucket_object_lock_configuration" "audit" {
  count  = var.audit_object_lock_enabled ? 1 : 0
  bucket = aws_s3_bucket.this["audit"].id
  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = var.audit_object_lock_retention_days
    }
  }
  depends_on = [aws_s3_bucket_versioning.this]
}

data "aws_iam_policy_document" "bucket_tls" {
  for_each = aws_s3_bucket.this
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [each.value.arn, "${each.value.arn}/*"]
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
}

resource "aws_s3_bucket_policy" "standard" {
  for_each = { for key, value in aws_s3_bucket.this : key => value if key != "alb_logs" }
  bucket   = each.value.id
  policy   = data.aws_iam_policy_document.bucket_tls[each.key].json
}

data "aws_iam_policy_document" "alb_logs" {
  source_policy_documents = [data.aws_iam_policy_document.bucket_tls["alb_logs"].json]
  statement {
    sid       = "ALBLogDelivery"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.this["alb_logs"].arn}/AWSLogs/${data.aws_caller_identity.current.account_id}/*"]
    principals {
      type        = "Service"
      identifiers = ["logdelivery.elasticloadbalancing.amazonaws.com"]
    }
  }
  statement {
    sid       = "ALBLogDeliveryAclCheck"
    effect    = "Allow"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.this["alb_logs"].arn]
    principals {
      type        = "Service"
      identifiers = ["logdelivery.elasticloadbalancing.amazonaws.com"]
    }
  }
}

resource "aws_s3_bucket_policy" "alb_logs" {
  bucket = aws_s3_bucket.this["alb_logs"].id
  policy = data.aws_iam_policy_document.alb_logs.json
}
