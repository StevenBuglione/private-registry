output "replication_role_arn" { value = try(aws_iam_role.s3_replication[0].arn, null) }
