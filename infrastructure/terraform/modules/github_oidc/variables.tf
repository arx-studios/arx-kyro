variable "name" {
  description = "Name prefix, for example kyro-staging."
  type        = string
}

variable "github_repo" {
  description = "GitHub repository as owner/name. Trust policies are scoped to exactly this repository."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repo))
    error_message = "github_repo must look like owner/name."
  }
}

variable "github_environment" {
  description = "GitHub environment name the deploy role is trusted for."
  type        = string
  default     = "staging"
}

variable "create_oidc_provider" {
  description = "Create the GitHub OIDC provider. Set false if the account already has one (then pass oidc_provider_arn)."
  type        = bool
  default     = true
}

variable "oidc_provider_arn" {
  description = "Existing GitHub OIDC provider ARN (only when create_oidc_provider is false)."
  type        = string
  default     = ""
}

variable "state_bucket_name" {
  description = "Terraform state bucket the plan role may read (and lock in)."
  type        = string
}

variable "state_key" {
  description = "Object key of the staging state file in the state bucket."
  type        = string
  default     = "kyro/staging/terraform.tfstate"
}

variable "documents_bucket_arn" {
  description = "Documents bucket ARN (explicitly denied to the plan role so it can never read user documents)."
  type        = string
}

variable "ecr_repository_arns" {
  description = "ECR repository ARNs the deploy role may push to."
  type        = list(string)
}

variable "ecs_cluster_arn" {
  description = "ECS cluster ARN the deploy role may deploy to."
  type        = string
}

variable "ecs_service_arns_pattern" {
  description = "ECS service ARN pattern for UpdateService, for example arn:aws:ecs:region:account:service/kyro-staging/*."
  type        = string
}

variable "task_role_arns" {
  description = "Task and execution role ARNs the deploy role may pass to ECS."
  type        = list(string)
}
