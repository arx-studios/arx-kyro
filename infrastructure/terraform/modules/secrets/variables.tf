variable "name" {
  description = "Secret path prefix, for example kyro/staging."
  type        = string
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key ARN for the secrets."
  type        = string
}

variable "secret_names" {
  description = "Secret names created WITHOUT values. Values are set out of band (see the runbook)."
  type        = set(string)
  default     = ["mfa-encryption-key", "mail-smtp"]
}

variable "recovery_window_in_days" {
  description = "Recovery window on deletion."
  type        = number
  default     = 30
}
