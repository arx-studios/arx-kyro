variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "account_id" {
  description = "AWS account id (variable so plans work offline)."
  type        = string
}

variable "purposes" {
  description = "Customer-managed key purposes to create."
  type        = set(string)
  default     = ["documents", "rds", "secrets", "ecr"]
}

variable "deletion_window_in_days" {
  description = "KMS key deletion window."
  type        = number
  default     = 30
}
