variable "name" {
  description = "Service name (api, web, admin). Resources are prefixed with cluster_name."
  type        = string
}

variable "cluster_name" {
  description = "ECS cluster name, used as a prefix, for example kyro-staging."
  type        = string
}

variable "cluster_arn" {
  description = "ECS cluster ARN."
  type        = string
}

variable "namespace_arn" {
  description = "Service Connect namespace ARN."
  type        = string
}

variable "vpc_id" {
  description = "VPC id."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR (egress to VPC services such as RDS and Service Connect peers)."
  type        = string
}

variable "subnet_ids" {
  description = "Private application subnet ids."
  type        = list(string)
}

variable "image" {
  description = "Full container image reference including an immutable tag."
  type        = string
}

variable "ecr_repository_arn" {
  description = "ARN of the ECR repository the image comes from (execution role pull access)."
  type        = string
}

variable "container_port" {
  description = "Container port."
  type        = number
}

variable "cpu" {
  description = "Task CPU units."
  type        = number
  default     = 512
}

variable "memory" {
  description = "Task memory in MiB."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Number of tasks."
  type        = number
  default     = 1
}

variable "environment" {
  description = "Plain environment variables."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Environment variable name to Secrets Manager valueFrom (arn or arn:json-key::)."
  type        = map(string)
  default     = {}
}

variable "secret_arns" {
  description = "Base ARNs of every secret referenced in secrets (execution role read access)."
  type        = list(string)
  default     = []
}

variable "secrets_kms_key_arn" {
  description = "KMS key ARN that encrypts the referenced secrets."
  type        = string
}

variable "task_role_policy_json" {
  description = "Optional inline policy JSON for the task role (least privilege, application permissions)."
  type        = string
  default     = ""
}

variable "attach_task_role_policy" {
  description = "Attach task_role_policy_json to the task role. A separate bool because the policy JSON is unknown at plan time."
  type        = bool
  default     = false
}

variable "target_group_arn" {
  description = "ALB target group ARN. Set it only together with has_target_group = true."
  type        = string
  default     = ""
}

variable "has_target_group" {
  description = "Whether the service registers with an ALB target group. A separate bool because target_group_arn is unknown at plan time."
  type        = bool
  default     = false
}

variable "ingress_security_group_ids" {
  description = "Map of label to security group id allowed to reach container_port."
  type        = map(string)
  default     = {}
}

variable "service_connect_alias" {
  description = "When set, publish this port as a Service Connect service with this DNS name (api -> http://api:port)."
  type        = string
  default     = ""
}

variable "health_check_grace_period_seconds" {
  description = "Grace period before ALB health checks count (only with a target group)."
  type        = number
  default     = 120
}

variable "log_retention_days" {
  description = "CloudWatch log retention."
  type        = number
  default     = 30
}

variable "region" {
  description = "AWS region (awslogs driver)."
  type        = string
}
