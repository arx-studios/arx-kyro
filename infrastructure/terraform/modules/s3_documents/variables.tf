variable "bucket_name" {
  description = "Globally unique documents bucket name."
  type        = string
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key ARN for SSE-KMS."
  type        = string
}

variable "web_origin" {
  description = "Web origin (https://host) allowed to PUT through presigned URLs (CORS)."
  type        = string
}

variable "noncurrent_version_expiration_days" {
  description = "Days after which noncurrent object versions expire."
  type        = number
  default     = 90
}

variable "enable_malware_protection" {
  description = "Create the GuardDuty S3 malware protection plan (scan on upload, tags objects). App-side gating is Phase 6."
  type        = bool
  default     = true
}
