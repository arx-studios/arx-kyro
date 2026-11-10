variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "vpc_id" {
  description = "VPC id."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR (egress to the ECS tasks)."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet ids for the load balancer."
  type        = list(string)
}

variable "cloudfront_prefix_list_id" {
  description = "Id of the AWS-managed CloudFront origin-facing prefix list in this region (com.amazonaws.global.cloudfront.origin-facing). Only CloudFront may reach the ALB."
  type        = string
}

variable "acm_certificate_arn" {
  description = "Regional ACM certificate ARN for the HTTPS listener (covers the web and admin hosts)."
  type        = string
}

variable "web_host" {
  description = "Public web hostname."
  type        = string
}

variable "admin_host" {
  description = "Public admin hostname."
  type        = string
}

variable "ports" {
  description = "Container ports of the target services."
  type        = map(number)
  default = {
    api   = 8080
    web   = 3000
    admin = 3001
  }
}
