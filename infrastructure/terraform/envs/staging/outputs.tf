output "cloudfront_domain_name" {
  description = "Point the web and admin hostnames (CNAME/ALIAS) at this CloudFront domain."
  value       = module.edge.distribution_domain_name
}

output "documents_bucket" {
  description = "Private documents bucket."
  value       = module.s3_documents.bucket_name
}

output "ecr_repository_urls" {
  description = "ECR repositories for api, web and admin images."
  value       = module.ecr.repository_urls
}

output "rds_address" {
  description = "Postgres endpoint (private subnets only)."
  value       = module.rds.address
}

output "secret_arns" {
  description = "Secrets that must be given values out of band (mfa-encryption-key, mail-smtp)."
  value       = module.secrets.secret_arns
}

output "github_plan_role_arn" {
  description = "GitHub OIDC read-only plan role. Nothing assumes it in Phase 5."
  value       = module.github_oidc.plan_role_arn
}

output "github_deploy_role_arn" {
  description = "GitHub OIDC deploy role (environment staging only). Nothing assumes it in Phase 5."
  value       = module.github_oidc.deploy_role_arn
}

output "ses_dkim_tokens" {
  description = "SES DKIM tokens (empty while enable_ses is false)."
  value       = module.ses.dkim_tokens
}
