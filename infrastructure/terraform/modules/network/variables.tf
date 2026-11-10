variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "region" {
  description = "AWS region (used for VPC endpoint service names)."
  type        = string
}

variable "azs" {
  description = "Exactly two availability zone names. A variable, not a data source, so plans work offline."
  type        = list(string)

  validation {
    condition     = length(var.azs) == 2
    error_message = "Provide exactly two availability zones."
  }
}

variable "vpc_cidr" {
  description = "VPC CIDR block."
  type        = string
  default     = "10.20.0.0/16"
}

variable "flow_log_retention_days" {
  description = "Retention for VPC flow logs."
  type        = number
  default     = 30
}
