output "address" {
  description = "Database hostname."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Database port."
  value       = aws_db_instance.this.port
}

output "db_name" {
  description = "Initial database name."
  value       = aws_db_instance.this.db_name
}

output "identifier" {
  description = "DB instance identifier (CloudWatch dimension)."
  value       = aws_db_instance.this.identifier
}

output "master_secret_arn" {
  description = "ARN of the RDS-managed master user secret (JSON with username and password)."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "security_group_id" {
  description = "Database security group id."
  value       = aws_security_group.db.id
}
