output "cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.this.arn
}

output "cluster_name" {
  description = "ECS cluster name (CloudWatch dimension)."
  value       = aws_ecs_cluster.this.name
}

output "namespace_arn" {
  description = "Service Connect namespace ARN."
  value       = aws_service_discovery_http_namespace.this.arn
}
