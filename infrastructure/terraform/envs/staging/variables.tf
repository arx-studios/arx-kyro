variable "offline_plan" {
  description = "CI only: skip credential, account and metadata lookups so `terraform plan` runs with mock credentials. Never set for a real plan or apply."
  type        = bool
  default     = false
}

variable "region" {
  description = "Primary AWS region."
  type        = string
  default     = "eu-west-1"
}

variable "account_id" {
  description = "AWS account id. A variable (not a data source) so plans work offline. The example file uses the fictional 000000000000."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12 digit AWS account id."
  }
}

variable "azs" {
  description = "Exactly two availability zone names in the region."
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b"]
}

variable "vpc_cidr" {
  description = "VPC CIDR."
  type        = string
  default     = "10.20.0.0/16"
}

variable "web_host" {
  description = "Public web hostname, for example staging.example.com."
  type        = string
}

variable "admin_host" {
  description = "Public admin hostname, for example admin.staging.example.com."
  type        = string
}

variable "alb_certificate_arn" {
  description = "Regional ACM certificate ARN (in var.region) covering web_host and admin_host, for the ALB HTTPS listener."
  type        = string
}

variable "cloudfront_certificate_arn" {
  description = "ACM certificate ARN in us-east-1 covering web_host and admin_host, for CloudFront."
  type        = string
}

variable "cloudfront_prefix_list_id" {
  description = "Id of the AWS-managed prefix list com.amazonaws.global.cloudfront.origin-facing in var.region (look it up once with the AWS CLI; see the runbook)."
  type        = string
}

variable "admin_allowed_cidrs" {
  description = "IPv4 CIDRs allowed to reach the admin hostname through the WAF. Empty blocks it for everyone."
  type        = list(string)
  default     = []
}

variable "github_repo" {
  description = "GitHub repository (owner/name) that the OIDC roles trust."
  type        = string
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub OIDC provider (false if the account already has one)."
  type        = bool
  default     = true
}

variable "github_oidc_provider_arn" {
  description = "Existing GitHub OIDC provider ARN when create_github_oidc_provider is false."
  type        = string
  default     = ""
}

variable "state_bucket_name" {
  description = "Terraform state bucket (created manually, see the runbook)."
  type        = string
}

variable "image_tag" {
  description = "Immutable image tag deployed to all three services (for example a git SHA)."
  type        = string
  default     = "bootstrap"
}

variable "api_desired_count" {
  description = "Number of api tasks. MUST stay 1: sessions and rate-limit counters are in memory until Spring Session JDBC lands (Phase 6)."
  type        = number
  default     = 1

  validation {
    condition     = var.api_desired_count == 1
    error_message = "The api must run exactly one task in staging (in-memory sessions and rate limits). Lift this only after Spring Session JDBC / Redis rate limits ship."
  }
}

variable "web_desired_count" {
  description = "Number of web tasks."
  type        = number
  default     = 1
}

variable "admin_desired_count" {
  description = "Number of admin tasks."
  type        = number
  default     = 1
}

variable "mail_host" {
  description = "SMTP host used by the api (for example email-smtp.<region>.amazonaws.com)."
  type        = string
  default     = "email-smtp.eu-west-1.amazonaws.com"
}

variable "mail_from" {
  description = "From address for transactional email."
  type        = string
  default     = "no-reply@staging.example.com"
}

variable "alarm_sns_topic_arn" {
  description = "SNS topic ARN for alarm notifications (empty = alarms without actions)."
  type        = string
  default     = ""
}

variable "enable_ses" {
  description = "Create the SES domain identity (off until real sending is approved)."
  type        = bool
  default     = false
}

variable "ses_domain" {
  description = "SES sending domain (used when enable_ses is true)."
  type        = string
  default     = ""
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "log_retention_days" {
  description = "CloudWatch log retention for ECS services."
  type        = number
  default     = 30
}
