variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "vpc_id" {
  description = "VPC id."
  type        = string
}

variable "db_subnet_ids" {
  description = "Isolated database subnet ids."
  type        = list(string)
}

variable "rds_kms_key_arn" {
  description = "KMS key ARN for storage and Performance Insights encryption."
  type        = string
}

variable "secrets_kms_key_arn" {
  description = "KMS key ARN encrypting the RDS-managed master user secret."
  type        = string
}

variable "db_name" {
  description = "Initial database name."
  type        = string
  default     = "kyro"
}

variable "master_username" {
  description = "Master username (the password is generated and stored by RDS in Secrets Manager)."
  type        = string
  default     = "kyro"
}

variable "instance_class" {
  description = "Instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Initial storage in GiB."
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Storage autoscaling ceiling in GiB."
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "Multi-AZ deployment (off for staging cost)."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Automated backup retention (enables PITR)."
  type        = number
  default     = 7
}
