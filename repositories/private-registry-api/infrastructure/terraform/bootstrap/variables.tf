variable "aws_region" {
  description = "AWS Region for the state bucket."
  type        = string
}

variable "state_bucket_name" {
  description = "Globally unique S3 state bucket name."
  type        = string
}

variable "log_bucket_name" {
  description = "Globally unique S3 access-log bucket name."
  type        = string
}

variable "state_key_administrator_arns" {
  description = "IAM principals allowed to administer the state KMS key."
  type        = list(string)
  default     = []
}

variable "state_reader_writer_arns" {
  description = "IAM principals allowed to use the state key and bucket."
  type        = list(string)
  default     = []
}

variable "force_destroy" {
  description = "Development-only state bucket deletion switch. Must remain false for shared environments."
  type        = bool
  default     = false
}

variable "tags" {
  type    = map(string)
  default = {}
}
