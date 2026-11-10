output "dkim_tokens" {
  description = "DKIM tokens: create three CNAMEs <token>._domainkey.<domain> -> <token>.dkim.amazonses.com. Empty when SES is disabled."
  value       = var.enable_ses ? aws_sesv2_email_identity.this[0].dkim_signing_attributes[0].tokens : []
}

output "identity_arn" {
  description = "SES identity ARN. Empty when SES is disabled."
  value       = var.enable_ses ? aws_sesv2_email_identity.this[0].arn : ""
}
