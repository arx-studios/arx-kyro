variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "repositories" {
  description = "Repository short names (images kyro-staging/<name>)."
  type        = set(string)
  default     = ["api", "web", "admin"]
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key ARN for repository encryption."
  type        = string
}

variable "keep_last_images" {
  description = "Number of tagged images to retain per repository."
  type        = number
  default     = 20
}
