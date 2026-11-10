output "vpc_id" {
  description = "VPC id."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "VPC CIDR."
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "Public subnets (ALB, NAT)."
  value       = [for s in aws_subnet.public : s.id]
}

output "private_subnet_ids" {
  description = "Private application subnets (ECS tasks)."
  value       = [for s in aws_subnet.private : s.id]
}

output "db_subnet_ids" {
  description = "Isolated database subnets."
  value       = [for s in aws_subnet.db : s.id]
}
