resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# Service Connect namespace: web and admin reach the api as http://api:8080.
resource "aws_service_discovery_http_namespace" "this" {
  name        = "${var.name}.internal"
  description = "Service Connect namespace for ${var.name}"
}
