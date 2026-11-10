variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "origin_domain_name" {
  description = "ALB DNS name used as the CloudFront origin."
  type        = string
}

variable "web_host" {
  description = "Public web hostname (CloudFront alias)."
  type        = string
}

variable "admin_host" {
  description = "Public admin hostname (CloudFront alias, restricted by the WAF)."
  type        = string
}

variable "cloudfront_certificate_arn" {
  description = "ACM certificate ARN in us-east-1 covering web_host and admin_host."
  type        = string
}

variable "admin_allowed_cidrs" {
  description = "IPv4 CIDRs allowed to reach the admin hostname. Empty means the admin host is blocked for everyone."
  type        = list(string)
  default     = []
}

variable "rate_limit_per_5_minutes" {
  description = "Requests per 5 minutes per source IP before the rate-based rule blocks."
  type        = number
  default     = 2000
}
