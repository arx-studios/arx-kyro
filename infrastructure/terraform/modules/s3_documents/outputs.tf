output "bucket_name" {
  description = "Documents bucket name."
  value       = aws_s3_bucket.this.id
}

output "bucket_arn" {
  description = "Documents bucket ARN."
  value       = aws_s3_bucket.this.arn
}
