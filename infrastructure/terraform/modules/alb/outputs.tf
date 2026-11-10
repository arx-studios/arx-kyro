output "dns_name" {
  description = "ALB DNS name (CloudFront origin)."
  value       = aws_lb.this.dns_name
}

output "arn_suffix" {
  description = "ALB ARN suffix (CloudWatch dimension)."
  value       = aws_lb.this.arn_suffix
}

output "security_group_id" {
  description = "ALB security group id."
  value       = aws_security_group.this.id
}

output "target_group_arns" {
  description = "Map of service to target group ARN."
  value       = { for k, v in aws_lb_target_group.this : k => v.arn }
}
