output "key_arns" {
  description = "Map of purpose to key ARN."
  value       = { for k, v in aws_kms_key.this : k => v.arn }
}

output "key_ids" {
  description = "Map of purpose to key id."
  value       = { for k, v in aws_kms_key.this : k => v.key_id }
}
