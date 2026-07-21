resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
  tags            = local.common_tags
}

locals {
  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.github_oidc_provider_arn
  github_oidc_enabled      = local.github_oidc_provider_arn != null && var.github_owner != null

  github_roles = local.github_oidc_enabled ? {
    terraform-plan  = { repository = var.github_api_repository, policy_arns = var.github_terraform_plan_policy_arns }
    terraform-apply = { repository = var.github_api_repository, policy_arns = var.github_terraform_apply_policy_arns }
    api-images      = { repository = var.github_api_repository, policy_arns = var.github_image_policy_arns }
    ui-images       = { repository = var.github_ui_repository, policy_arns = var.github_image_policy_arns }
    migrations      = { repository = var.github_api_repository, policy_arns = var.github_migration_policy_arns }
  } : {}

  github_policy_attachments = {
    for item in flatten([
      for role_name, role in local.github_roles : [
        for policy_arn in role.policy_arns : {
          key        = "${role_name}-${replace(policy_arn, ":", "-")}"
          role_name  = role_name
          policy_arn = policy_arn
        }
      ]
    ]) : item.key => item
  }
}

data "aws_iam_policy_document" "github_assume" {
  for_each = local.github_roles

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [for suffix in var.github_allowed_refs : "repo:${var.github_owner}/${each.value.repository}:${suffix}"]
    }
  }
}

resource "aws_iam_role" "github" {
  for_each = local.github_roles

  name                 = "${local.name}-github-${each.key}"
  assume_role_policy   = data.aws_iam_policy_document.github_assume[each.key].json
  permissions_boundary = var.github_permissions_boundary_arn
  max_session_duration = each.key == "terraform-apply" ? 7200 : 3600
  tags                 = merge(local.common_tags, { GitHubRepository = each.value.repository })
}

resource "aws_iam_role_policy_attachment" "github_supplied" {
  for_each = local.github_policy_attachments

  role       = aws_iam_role.github[each.value.role_name].name
  policy_arn = each.value.policy_arn
}

data "aws_iam_policy_document" "github_images" {
  statement {
    sid       = "ECRAuthorization"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  statement {
    sid = "PushImages"
    actions = [
      "ecr:BatchCheckLayerAvailability", "ecr:CompleteLayerUpload", "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart"
    ]
    resources = values(aws_ecr_repository.service)[*].arn
  }
  statement {
    sid       = "DescribeDeployments"
    actions   = ["ecs:DescribeClusters", "ecs:DescribeServices", "ecs:DescribeTaskDefinition", "ecs:ListTaskDefinitions"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "github_images" {
  count = local.github_oidc_enabled ? 1 : 0

  name   = "${local.name}-github-images"
  policy = data.aws_iam_policy_document.github_images.json
  tags   = local.common_tags
}

resource "aws_iam_role_policy_attachment" "github_api_images" {
  count      = local.github_oidc_enabled ? 1 : 0
  role       = aws_iam_role.github["api-images"].name
  policy_arn = aws_iam_policy.github_images[0].arn
}

resource "aws_iam_role_policy_attachment" "github_ui_images" {
  count      = local.github_oidc_enabled ? 1 : 0
  role       = aws_iam_role.github["ui-images"].name
  policy_arn = aws_iam_policy.github_images[0].arn
}

data "aws_iam_policy_document" "github_migrations" {
  statement {
    actions   = ["ecs:RunTask", "ecs:DescribeTasks"]
    resources = ["*"]
    condition {
      test     = "ArnEquals"
      variable = "ecs:cluster"
      values   = [aws_ecs_cluster.this.arn]
    }
  }
  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn, aws_iam_role.migrations_task.arn]
  }
}

resource "aws_iam_policy" "github_migrations" {
  count = local.github_oidc_enabled ? 1 : 0

  name   = "${local.name}-github-migrations"
  policy = data.aws_iam_policy_document.github_migrations.json
  tags   = local.common_tags
}

resource "aws_iam_role_policy_attachment" "github_migrations" {
  count      = local.github_oidc_enabled ? 1 : 0
  role       = aws_iam_role.github["migrations"].name
  policy_arn = aws_iam_policy.github_migrations[0].arn
}
