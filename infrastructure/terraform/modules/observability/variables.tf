variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "alarm_sns_topic_arn" {
  description = "SNS topic ARN notified by alarms. Empty creates the alarms without actions."
  type        = string
  default     = ""
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix."
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS cluster name."
  type        = string
}

variable "ecs_service_names" {
  description = "ECS service names to alarm on (CPU and memory)."
  type        = set(string)
}

variable "rds_instance_identifier" {
  description = "RDS DB instance identifier."
  type        = string
}

variable "rds_free_storage_bytes_threshold" {
  description = "Alarm when free storage drops below this many bytes."
  type        = number
  default     = 5368709120
}

variable "cpu_threshold_percent" {
  description = "CPU alarm threshold."
  type        = number
  default     = 80
}

variable "memory_threshold_percent" {
  description = "Memory alarm threshold."
  type        = number
  default     = 85
}
