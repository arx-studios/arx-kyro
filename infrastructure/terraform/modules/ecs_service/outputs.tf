output "service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.this.name
}

output "security_group_id" {
  description = "Task security group id."
  value       = aws_security_group.this.id
}

output "task_role_arn" {
  description = "Task role ARN."
  value       = aws_iam_role.task.arn
}

output "execution_role_arn" {
  description = "Execution role ARN."
  value       = aws_iam_role.execution.arn
}

output "log_group_name" {
  description = "CloudWatch log group name."
  value       = aws_cloudwatch_log_group.this.name
}
