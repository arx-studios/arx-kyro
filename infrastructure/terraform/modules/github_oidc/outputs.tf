output "plan_role_arn" {
  description = "Read-only plan role (trusted for pull_request of the repository). Nothing assumes it in Phase 5."
  value       = aws_iam_role.plan.arn
}

output "deploy_role_arn" {
  description = "Deploy role (trusted for the staging environment only). Nothing assumes it in Phase 5."
  value       = aws_iam_role.deploy.arn
}
