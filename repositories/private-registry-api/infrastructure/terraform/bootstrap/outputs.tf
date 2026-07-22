output "state_bucket_name" { value = aws_s3_bucket.state.id }
output "state_bucket_arn" { value = aws_s3_bucket.state.arn }
output "state_kms_key_arn" { value = aws_kms_key.state.arn }
output "log_bucket_name" { value = aws_s3_bucket.logs.id }
