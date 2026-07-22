provider "aws" {
  alias  = "primary"
  region = var.primary_region
}
provider "aws" {
  alias  = "dr"
  region = var.dr_region
}

data "aws_caller_identity" "primary" { provider = aws.primary }
data "aws_partition" "current" { provider = aws.primary }

resource "aws_ecr_replication_configuration" "this" {
  provider = aws.primary
  replication_configuration {
    rule {
      destination {
        region      = var.dr_region
        registry_id = var.dr_account_id
      }
      repository_filter {
        filter      = var.ecr_repository_prefix
        filter_type = "PREFIX_MATCH"
      }
    }
  }
}

data "aws_iam_policy_document" "s3_replication_assume" {
  provider = aws.primary
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["s3.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "s3_replication" {
  provider           = aws.primary
  count              = length(var.bucket_replications) == 0 ? 0 : 1
  name               = "private-registry-s3-replication"
  assume_role_policy = data.aws_iam_policy_document.s3_replication_assume.json
  tags               = var.tags
}

data "aws_iam_policy_document" "s3_replication" {
  provider = aws.primary
  count    = length(var.bucket_replications) == 0 ? 0 : 1

  statement {
    actions   = ["s3:GetReplicationConfiguration", "s3:ListBucket"]
    resources = [for item in values(var.bucket_replications) : item.source_bucket_arn]
  }
  statement {
    actions   = ["s3:GetObjectVersionForReplication", "s3:GetObjectVersionAcl", "s3:GetObjectVersionTagging"]
    resources = [for item in values(var.bucket_replications) : "${item.source_bucket_arn}/*"]
  }
  statement {
    actions   = ["s3:ReplicateObject", "s3:ReplicateDelete", "s3:ReplicateTags"]
    resources = [for item in values(var.bucket_replications) : "${item.destination_bucket_arn}/*"]
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = distinct([for item in values(var.bucket_replications) : item.source_kms_key_arn])
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["s3.${var.primary_region}.amazonaws.com"]
    }
  }
  statement {
    actions   = ["kms:Encrypt", "kms:GenerateDataKey"]
    resources = distinct([for item in values(var.bucket_replications) : item.destination_kms_key_arn])
  }
}

resource "aws_iam_role_policy" "s3_replication" {
  provider = aws.primary
  count    = length(var.bucket_replications) == 0 ? 0 : 1
  name     = "replicate-private-registry-buckets"
  role     = aws_iam_role.s3_replication[0].id
  policy   = data.aws_iam_policy_document.s3_replication[0].json
}

resource "aws_s3_bucket_replication_configuration" "this" {
  provider = aws.primary
  for_each = var.bucket_replications

  role   = aws_iam_role.s3_replication[0].arn
  bucket = each.value.source_bucket_id

  rule {
    id       = "replicate-to-${var.dr_region}"
    priority = 1
    status   = "Enabled"
    filter {}
    delete_marker_replication { status = "Enabled" }
    source_selection_criteria {
      sse_kms_encrypted_objects { status = "Enabled" }
    }
    destination {
      bucket        = each.value.destination_bucket_arn
      storage_class = "STANDARD"
      encryption_configuration { replica_kms_key_id = each.value.destination_kms_key_arn }
    }
  }
}

resource "aws_route53_record" "primary" {
  provider       = aws.primary
  zone_id        = var.route53_zone_id
  name           = var.registry_dns_name
  type           = "A"
  set_identifier = "primary"
  failover_routing_policy { type = "PRIMARY" }
  alias {
    name                   = var.primary_alb_dns_name
    zone_id                = var.primary_alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "dr" {
  provider       = aws.primary
  zone_id        = var.route53_zone_id
  name           = var.registry_dns_name
  type           = "A"
  set_identifier = "dr"
  failover_routing_policy { type = "SECONDARY" }
  alias {
    name                   = var.dr_alb_dns_name
    zone_id                = var.dr_alb_zone_id
    evaluate_target_health = true
  }
}
