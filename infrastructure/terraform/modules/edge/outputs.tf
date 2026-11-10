output "distribution_domain_name" {
  description = "CloudFront domain name. Point the web and admin hostnames (CNAME/ALIAS) here."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "distribution_id" {
  description = "CloudFront distribution id."
  value       = aws_cloudfront_distribution.this.id
}

output "web_acl_arn" {
  description = "WAFv2 web ACL ARN."
  value       = aws_wafv2_web_acl.this.arn
}
